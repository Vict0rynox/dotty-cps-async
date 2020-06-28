package cps

import scala.quoted._

trait CpsMonad[F[_]] 

trait ComputationBound[T] 

implicit object ComputationBoundMonad extends CpsMonad[ComputationBound] 

inline def async[F[_]](using am:CpsMonad[F]): Async.InferAsyncArg[F] =
   new Async.InferAsyncArg[F]

object PFHelpr
{
  def create[X,Y](x:String):PartialFunction[X,Y]=???
}

object Async {

  class InferAsyncArg[F[_]](using am:CpsMonad[F]) {

       inline def apply[T](inline expr: T):Unit =
       ${
         Async.checkPrintTypeImpl[F,T]('expr)
        }

  }


  def checkPrintTypeImpl[F[_]:Type,T:Type](f: Expr[T])(using qctx: QuoteContext): Expr[Unit] = 
    import qctx.tasty.{_,given _}

    def uninline(t:Term):Term =
      t match
        case Inlined(_,_,x) => uninline(x)
        case _ => t

    val fu = uninline(f.unseal)
    fu match 
              case Block(_,Apply(TypeApply(Select(q,n),tparams),List(param))) =>
                   param.tpe match
                      case AppliedType(tp,tparams1) =>
                        val fType = summon[quoted.Type[F]]
                        val ptp = tparams1.tail.head
                        val ptpWrapped = AppliedType(fType.unseal.tpe,List(ptp))
                        val nothingType = defn.NothingType

                        if (nothingType <:< ptpWrapped) 
                           println("ok")
                        else
                           println("not ok")
                      case None => 
                        println(s"tpe=${param.tpe} is not AppliedType")
                   '{ () }
              case _ => ???


}
