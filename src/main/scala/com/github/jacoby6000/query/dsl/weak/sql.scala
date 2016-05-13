package com.github.jacoby6000.query.dsl.weak

import com.github.jacoby6000.query.ast._
import com.github.jacoby6000.query.shapeless.KindConstraint.OfKindContainingHListTC
import com.github.jacoby6000.query.shapeless.KindConstraint.OfKindContainingHListTC._
import shapeless.ops.hlist.{ Mapper, Prepend, ToTraversable }
import shapeless._

import scala.annotation.implicitNotFound
import scala.util.matching.Regex

/**
 * Created by jacob.barber on 3/4/16.
 */
object sql {

  //  object implicitConversions {
  //    implicit def selectBuilderToSelectQuery[A <: HList](queryBuilder: QueryBuilder[A]): QuerySelect[A] = queryBuilder.query
  //    implicit def updateBuilderToUpdateQuery[A <: HList](updateBuilder: UpdateBuilder[A]): QueryUpdate[A] = updateBuilder.update
  //  }
  // Delete DSL helpers
  //  def deleteFrom(table: QueryPath): DeleteBuilder = DeleteBuilder(table)
  //  case class DeleteBuilder(table: QueryPath) {
  //    def where(queryComparison: QueryComparison) = QueryDelete(table, queryComparison)
  //  }
  //
  //  // Update DSL helpers
  //  def update(table: QueryPath): UpdateBuilder = UpdateBuilder(QueryUpdate(table, List.empty, None))
  //
  //  case class UpdateBuilder(update: QueryUpdate) {
  //    def set(modifyFields: ModifyField*): UpdateBuilder = UpdateBuilder(update.copy(values = update.values ::: modifyFields.toList))
  //    def where(where: QueryComparison): QueryUpdate = QueryUpdate(update.collection, update.values, Some(where))
  //  }
  //
  //  implicit class QueryPathUpdateExtensions(val queryPath: QueryPath) extends AnyVal {
  //    def ==>(value: QueryValue): ModifyField = ModifyField(queryPath, value)
  //  }
  //
  //  // Insert DSL helpers
  //  def insertInto(table: QueryPath)(columns: QueryPath*): InsertBuilder = InsertBuilder(table, columns.toList)
  //
  //  case class InsertBuilder(table: QueryPath, columns: List[QueryPath]) {
  //    def values(values: QueryValue*): QueryInsert = QueryInsert(table, (columns zip values) map (kv => ModifyField(kv._1, kv._2)))
  //  }

  // Select/Query DSL helpers
  case class SqlQueryFunctionBuilder(f: QueryPath) extends ProductArgs {
    def apply[A <: HList](arg1: QueryValue[A]) = QueryFunction(f, arg1 :: HNil)

    def apply[A <: HList, B <: HList](arg1: QueryValue[A], arg2: QueryValue[B]) = QueryFunction(f, arg1 :: arg2 :: HNil)

    def apply[A <: HList, B <: HList, C <: HList](
      arg1: QueryValue[A],
      arg2: QueryValue[B],
      arg3: QueryValue[C]
    ) = QueryFunction(f, arg1 :: arg2 :: arg3 :: HNil)

    def applyProduct[A <: HList: OfKindContainingHList[QueryValue]#HL, Out <: HList](a: A)(implicit
      toList: ToTraversable.Aux[A, List, QueryValue[_ <: HList]],
      m: Mapper.Aux[QueryValueUnwrapper.type, A, Out]): QueryFunction[Out] = QueryFunction(f, a)
  }

  implicit class StringContextExtensions(val c: StringContext) extends AnyVal {
    def p(): QueryPath = {
      val -::- = scala.collection.immutable.::
      def go(remainingParts: List[String], queryPath: QueryPath): QueryPath = remainingParts match {
        case head -::- tail => go(tail, QueryPathCons(head, queryPath))
        case Nil => queryPath
      }

      val parts = c.parts.mkString.split('.').toList.reverse
      go(parts.tail, QueryPathEnd(parts.head))
    }

    def expr(args: String*)(implicit ev0: RawExpressionHandler[String]): QueryRawExpression[String] = {
      QueryRawExpression(c.standardInterpolator(identity, args))
    }

    def func(): SqlQueryFunctionBuilder = SqlQueryFunctionBuilder(p())
  }

  implicit class QueryPathExtensions(val f: QueryPath) extends AnyVal {
    def as(alias: String) = f match {
      case c: QueryPathCons => QueryProjectOne(c, Some(alias))
      case c: QueryPathEnd => QueryProjectOne(c, Some(alias))
    }

    def asc: QuerySortAsc = QuerySortAsc(f)
    def desc: QuerySortDesc = QuerySortDesc(f)
  }

  val `*` = QueryProjectAll
  val `?` = QueryParameter
  val `null` = QueryNull

  object select {
    def apply[A <: HList](arg1: QueryProjection[A]) = SelectBuilder(arg1 :: HNil)

    def apply[A <: HList, B <: HList](
      arg1: QueryProjection[A],
      arg2: QueryProjection[B]
    ) = SelectBuilder(arg1 :: arg2 :: HNil)

    def apply[A <: HList, B <: HList, C <: HList](
      arg1: QueryProjection[A],
      arg2: QueryProjection[B],
      arg3: QueryProjection[C]
    ) = SelectBuilder(arg1 :: arg2 :: arg3 :: HNil)

    def applyProduct[A <: HList: OfKindContainingHList[QueryProjection]#HL, Out <: HList](a: A)(implicit
      toList: ToTraversable.Aux[A, List, QueryProjection[_ <: HList]],
      m: Mapper.Aux[QueryProjectionUnwrapper.type, A, Out]) = SelectBuilder(a)

  }

  case class SelectBuilder[QueryProjections <: HList: OfKindContainingHList[QueryProjection]#HL, MappedValues <: HList](projections: QueryProjections)(implicit
    toList: ToTraversable.Aux[QueryProjections, List, QueryProjection[_ <: HList]],
      m: Mapper.Aux[QueryProjectionUnwrapper.type, QueryProjections, MappedValues]) {

    implicit def um: Mapper.Aux[QueryUnionUnwrapper.type, HNil.type, HNil.type] = new Mapper[QueryUnionUnwrapper.type, HNil.type] {
      type Out = HNil.type
      def apply(l: HNil.type): Out = HNil
    }

    val queryEqual = QueryEqual(QueryParameter(true), QueryParameter(true))

    def from[B <: HList, Out1 <: HList, Out2 <: HList, Out3 <: HList](path: QueryProjection[B])(implicit p1: Prepend.Aux[B, MappedValues, Out1]) =
      QueryBuilder(path, projections, HNil)
  }

  trait Joiner[F[_ <: HList]] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryUnion[Out]
  }

  private val leftJoiner = new Joiner[QueryLeftOuterJoin] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryLeftOuterJoin[Out] = QueryLeftOuterJoin(a, b)
  }

  private val rightJoiner = new Joiner[QueryRightOuterJoin] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryRightOuterJoin[Out] = QueryRightOuterJoin(a, b)
  }

  private val outerJoiner = new Joiner[QueryFullOuterJoin] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryFullOuterJoin[Out] = QueryFullOuterJoin(a, b)
  }

  private val crossJoiner = new Joiner[QueryCrossJoin] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryCrossJoin[Out] = QueryCrossJoin(a, b)
  }

  private val innerJoiner = new Joiner[QueryInnerJoin] {
    def join[A <: HList, B <: HList, Out <: HList](a: QueryProjectOne[A], b: QueryComparison[B])(implicit prepender: Prepend.Aux[A, B, Out]): QueryInnerJoin[Out] = QueryInnerJoin(a, b)
  }

  case class QueryBuilder[Table <: HList, QueryProjections <: HList: OfKindContainingHList[QueryProjection]#HL, QueryUnions <: HList: OfKindContainingHList[QueryUnion]#HL, QBMappedValues <: HList, QBMappedUnions <: HList, QBOut1 <: HList, Params <: HList](
      table: QueryProjection[Table],
      values: QueryProjections,
      unions: QueryUnions
  )(implicit
    mv: Mapper.Aux[QueryProjectionUnwrapper.type, QueryProjections, QBMappedValues],
      mu: Mapper.Aux[QueryUnionUnwrapper.type, QueryUnions, QBMappedUnions],
      qp1: Prepend.Aux[Table, QBMappedValues, QBOut1],
      qp2: Prepend.Aux[QBOut1, QBMappedUnions, Params],
      pl: ToTraversable.Aux[QueryProjections, List, QueryProjection[_ <: HList]],
      ul: ToTraversable.Aux[QueryUnions, List, QueryUnion[_ <: HList]]) { builder =>

    def leftOuterJoin[A <: HList](table: QueryProjectOne[A]): JoinBuilder[A, QueryLeftOuterJoin] = new JoinBuilder[A, QueryLeftOuterJoin](table, leftJoiner)
    def rightOuterJoin[A <: HList](table: QueryProjectOne[A]): JoinBuilder[A, QueryRightOuterJoin] = new JoinBuilder[A, QueryRightOuterJoin](table, rightJoiner)
    def innerJoin[A <: HList](table: QueryProjectOne[A]): JoinBuilder[A, QueryInnerJoin] = new JoinBuilder[A, QueryInnerJoin](table, innerJoiner)
    def fullOuterJoin[A <: HList](table: QueryProjectOne[A]): JoinBuilder[A, QueryFullOuterJoin] = new JoinBuilder[A, QueryFullOuterJoin](table, outerJoiner)
    def crossJoin[A <: HList](table: QueryProjectOne[A]): JoinBuilder[A, QueryCrossJoin] = new JoinBuilder[A, QueryCrossJoin](table, crossJoiner)

    /*    def where[A <: HList](comparison: QueryComparison[A]) = builder.copy(filter = QueryAnd(comparison, builder.filter))
    def orderBy(sorts: QuerySort*) = builder.copy(sorts = builder.sorts ::: sorts.toList)
    def groupBy(groups: QuerySort*) = builder.copy(groupings = builder.groupings ::: groups.toList)

    def offset(n: Int) = builder.copy(offset = Some(n))
    def limit(n: Int) = builder.copy(limit = Some(n))*/

    class JoinBuilder[A <: HList, F[_ <: HList]](table: QueryProjectOne[A], joiner: Joiner[F]) {
      def on[B <: HList, MappedUnions <: HList, Out1 <: HList, Out2 <: HList: OfKindContainingHList[QueryUnion]#HL, Out3 <: HList, P <: HList](comp: QueryComparison[B])(
        implicit
        p1: Prepend.Aux[A, B, Out1],
        p2: Prepend.Aux[QueryUnions, QueryUnion[Out1] :: HNil, Out2],
        mu2: Mapper.Aux[QueryUnionUnwrapper.type, Out2, MappedUnions],
        p3: Prepend.Aux[QBOut1, MappedUnions, P],
        ul2: ToTraversable.Aux[Out2, List, QueryUnion[_ <: HList]]
      ) =
        builder.copy(unions = unions ::: joiner.join(table, comp) :: HNil) /*(
          implicitly[OfKindContainingHList[QueryProjection]#HL[QueryProjections]],
          implicitly[OfKindContainingHList[QueryUnion]#HL[Out2]],
          mv, mu2, qp1, , implicitly, implicitly
        )*/
    }
  }

  implicit class QueryValueExtensions[A <: HList](val a: QueryValue[A]) {
    def >[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryGreaterThan(a, b)
    def >=[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryGreaterThanOrEqual(a, b)
    def <[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryLessThan(a, b)
    def <=[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryLessThanOrEqual(a, b)

    def ===[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryEqual(a, b)
    def !==[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryComparison[Out] = QueryNotEqual(a, b)

    def ++[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryValue[Out] = QueryAdd(a, b)
    def --[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryValue[Out] = QuerySub(a, b)
    def /[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryValue[Out] = QueryDiv(a, b)
    def **[B <: HList, Out <: HList](b: QueryValue[B])(implicit p: Prepend.Aux[A, B, Out]): QueryValue[Out] = QueryMul(a, b)

    def as(alias: String): QueryProjection[A] = QueryProjectOne(a, Some(alias))
  }

  implicit def toQueryValue[A](a: A)(implicit ev: A =:!= QueryParameter[_], ev2: A =:!= QueryComparison[_]): QueryValue[A :: HNil] = QueryParameter(a)

  def ![A <: HList](queryComparison: QueryComparison[A]): QueryNot[A] = QueryNot(queryComparison)

  implicit class QueryComparisonExtensions[A <: HList](val left: QueryComparison[A]) extends AnyVal {
    def not: QueryNot[A] = QueryNot(left)
    def and[B <: HList, Out <: HList](right: QueryComparison[B])(implicit prepend: Prepend.Aux[A, B, Out]) = QueryAnd(left, right)
    def or[B <: HList, Out <: HList](right: QueryComparison[B])(implicit prepend: Prepend.Aux[A, B, Out]) = QueryOr(left, right)
  }
}
