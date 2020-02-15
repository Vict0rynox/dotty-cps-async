package cps.forest

import scala.quoted._
import scala.quoted.matching._

import cps._
import cps.misc._


trait RepeatedTreeTransform[F[_]]:

  thisScope: TreeTransformScope[F] =>

  import qctx.tasty.{_, given _}

  def runRepeated(repeated: Repeated): CpsTree =
     val cpsElems = repeated.elems.map(runRoot)
     val isAsync = cpsElems.exists(_.isAsync)
     if (!isAsync) 
         CpsTree.pure(repeated)
     else
         ???     


object RepeatedTreeTransform:


  def run[F[_]:Type,T:Type](using qctx: QuoteContext)(cpsCtx: TransformationContext[F,T],
                         repeated: qctx.tasty.Repeated)
                                                       : CpsExpr[F,T] = {
                         
     val tmpFType = summon[Type[F]]
     class Bridge(tc:TransformationContext[F,T]) extends
                                                    TreeTransformScope[F]
                                                    with TreeTransformScopeInstance[F,T](tc) {

         implicit val fType: quoted.Type[F] = tmpFType
          
         def bridge(): CpsExpr[F,T] =
            val origin = repeated.asInstanceOf[qctx.tasty.Repeated]
            runRepeated(origin).toResult(cpsCtx.patternCode).asInstanceOf[CpsExpr[F,T]]
                        

     } 
     (new Bridge(cpsCtx)).bridge()
  }


