package cps.forest

import scala.quoted._

import cps.{TransformationContextMarker=>TCM,_}
import cps.misc._


class TryTransform[F[_]:Type,T:Type](cpsCtx: TransformationContext[F,T]):

  import cpsCtx._

  // case Try(body, cases, finalizer)
  def run(using qctx: QuoteContext)(body: qctx.reflect.Term,
                                    cases: List[qctx.reflect.CaseDef],
                                    finalizer: Option[qctx.reflect.Term]): CpsExpr[F,T] =
     import qctx.reflect._
     val cpsBody = Async.nestTransform(body.asExprOf[T],
                                            cpsCtx, TCM.TryBody)
     val cpsCaseDefs = cases.zipWithIndex.map((cd,i) => Async.nestTransform(
                                                  cd.rhs.asExprOf[T],
                                                  cpsCtx, TCM.TryCase(i)))
     val isCaseDefsAsync = cpsCaseDefs.exists(_.isAsync)
     val optCpsFinalizer = finalizer.map( x => Async.nestTransform[F,T,Unit](
                                        x.asExprOf[Unit], cpsCtx, TCM.TryFinally))
     val isFinalizerAsync = optCpsFinalizer.exists(_.isAsync)
     val isAsync = cpsBody.isAsync || isCaseDefsAsync || isFinalizerAsync

     def makeAsyncCaseDefs(): List[CaseDef] =
        ((cases lazyZip cpsCaseDefs) map { (frs,snd) =>
           CaseDef(frs.pattern, frs.guard, snd.transformed.unseal)
        }).toList

     def makeRestoreExpr(): Expr[Throwable => F[T]]  =
        val nCaseDefs = makeAsyncCaseDefs()
        val restoreExpr = '{ (ex: Throwable) => ${Match('ex.unseal, nCaseDefs).asExprOf[F[T]]} }
        restoreExpr.asExprOf[Throwable => F[T]]


     val builder = if (!isAsync) {
                      CpsExpr.sync(monad, patternCode)
                   } else {
                      val errorMonad = if (monad.unseal.tpe <:< TypeRepr.of[CpsTryMonad[F]]) {
                                          monad.asExprOf[CpsTryMonad[F]]
                                      } else {
                                          throw MacroError(s"${monad} should be instance of CpsTryMonad for try/catch support", patternCode)
                                      }
                      optCpsFinalizer match
                        case None =>
                           if (cpsCaseDefs.isEmpty)
                             cpsBody
                           else
                             cpsBody.syncOrigin match
                               case None =>
                                 CpsExpr.async[F,T](cpsCtx.monad,
                                  '{
                                     ${errorMonad}.restore(
                                       ${errorMonad}.tryImpure(
                                         ${cpsBody.transformed}
                                       )
                                      )(${makeRestoreExpr()})
                                  })
                               case Some(syncBody) =>
                                 val nBody = '{ ${monad}.pure($syncBody) }.unseal
                                 CpsExpr.async[F,T](cpsCtx.monad,
                                    Try(nBody, makeAsyncCaseDefs(), None).asExprOf[F[T]]
                                 )
                        case Some(cpsFinalizer) =>
                           if (cpsCaseDefs.isEmpty)
                             cpsBody.syncOrigin match
                               case None =>
                                 cpsFinalizer.syncOrigin match
                                   case Some(syncFinalizer) =>
                                      CpsExpr.async[F,T](cpsCtx.monad,
                                       '{
                                         ${errorMonad}.withAction(
                                            ${errorMonad}.tryImpure(
                                              ${cpsBody.transformed}
                                            )
                                         )(${syncFinalizer})
                                      })
                                   case None =>
                                      CpsExpr.async[F,T](cpsCtx.monad,
                                       '{
                                         ${errorMonad}.withAsyncAction(
                                            ${errorMonad}.tryImpure(
                                               ${cpsBody.transformed}
                                            )
                                         )(${cpsFinalizer.transformed})
                                      })
                               case Some(syncBody) =>
                                 cpsFinalizer.syncOrigin match
                                   case Some(syncFinalizer) =>
                                      CpsExpr.async[F,T](cpsCtx.monad, 
                                       '{
                                         ${errorMonad}.withAction(
                                           ${errorMonad}.tryPure($syncBody)
                                         )(${syncFinalizer})
                                      })
                                   case None =>
                                      CpsExpr.async[F,T](cpsCtx.monad,
                                       '{
                                         ${errorMonad}.withAsyncAction(
                                           ${errorMonad}.tryPure($syncBody)
                                         )(${cpsFinalizer.transformed})
                                      })
                           else
                             cpsBody.syncOrigin match
                               case Some(syncBody) =>
                                 cpsFinalizer.syncOrigin match
                                   case Some(syncFinalizer) =>
                                     CpsExpr.async[F,T](cpsCtx.monad,
                                      '{
                                         ${errorMonad}.withAction(
                                           ${errorMonad}.restore(
                                             ${errorMonad}.tryPure($syncBody)
                                           )(${makeRestoreExpr()})
                                         )($syncFinalizer)
                                     })
                                   case None =>
                                     CpsExpr.async[F,T](cpsCtx.monad,
                                      '{
                                         ${errorMonad}.withAsyncAction(
                                           ${errorMonad}.restore(
                                             ${errorMonad}.tryPure($syncBody)
                                           )(${makeRestoreExpr()})
                                         )(${cpsFinalizer.transformed})
                                      })
                               case None =>
                                 cpsFinalizer.syncOrigin match
                                   case Some(syncFinalizer) =>
                                     CpsExpr.async[F,T](cpsCtx.monad,
                                      '{
                                         ${errorMonad}.withAction(
                                           ${errorMonad}.restore(
                                             ${errorMonad}.tryImpure(
                                               ${cpsBody.transformed}
                                             )
                                           )(${makeRestoreExpr()})
                                         )($syncFinalizer)
                                     })
                                   case None =>
                                     CpsExpr.async[F,T](cpsCtx.monad,
                                      '{
                                         ${errorMonad}.withAsyncAction(
                                           ${errorMonad}.restore(
                                             ${errorMonad}.tryImpure(
                                               ${cpsBody.transformed}
                                             )
                                           )(${makeRestoreExpr()})
                                         )(${cpsFinalizer.transformed})
                                     })
                   }
     builder


