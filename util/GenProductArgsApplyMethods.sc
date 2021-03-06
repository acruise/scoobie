import scoobie.doobie._
import scoobie.doobie.doo.postgres.interpreter
import scoobie.dsl.weak.sql._

val q =
  select (
    p"foo" + 10 as "woozle",
    `*`
  ) from (
    p"bar"
  ) leftOuterJoin (
    p"baz" as "b" on (
      p"bar.id" === p"b.barId"
    )
  ) innerJoin (
    p"biz" as "c" on (
      p"c.id" === p"bar.bizId"
    )
  ) where (
    p"c.name" === "LightSaber" and
    p"c.age" > 27
  ) orderBy p"c.age".desc groupBy p"b.worth".asc

val sql = q.build.genSql

//import scalaz._
//import Scalaz._
//
//val types = 'A' to 'Z'
//val paramLists = types.scanLeft(List.empty[Char]){ case (a,b) => b :: a} map (_.reverse)
//
//case class ApplyArguments(idx: Int, typeList: List[Char])
//case class ApplyImplicits(types: List[String], evidence: List[String])
//
//implicit val applyImplSemigroup = Monoid.instance[ApplyImplicits]((a,b) => ApplyImplicits(a.types ::: b.types, a.evidence ::: b.evidence), ApplyImplicits(List.empty, List.empty))
//
//def makeProductApply(argKind: String, subTypeOf: List[String], contextBounds: List[String], evidences: List[ApplyArguments => ApplyImplicits]): List[String] = {
//
//  val subTypeConstraints = subTypeOf match {
//    case Nil => ""
//    case constraints => " <: " + constraints.mkString(" <: ")
//  }
//
//  val contextBoundConstraints = contextBounds match {
//    case Nil => ""
//    case constraints => " : " + constraints.mkString(" : ")
//  }
//
//  val typeConstraints = subTypeConstraints + contextBoundConstraints
//  paramLists.map {
//    case Nil => """def apply = applyProduct(HNil: HNil)"""
//    case typeList =>
//      val ApplyImplicits(additionalTypes, evidenceList) = evidences.zipWithIndex.map(tup => tup._1(ApplyArguments(tup._2, typeList))).suml
//
//      val args = typeList.map(c => c.toLower + s": $argKind[" + c + "]")
//      val argNames = typeList.map(c => c.toLower)
//      val typesWithBounds = typeList.map(_ + typeConstraints)
//      s"""def apply[${typesWithBounds.mkString(", ")}, ${additionalTypes.mkString(", ")}](${args.mkString(", ")})(implicit ${evidenceList.mkString(", ")}) = applyProduct(${argNames.mkString("", " :: ", " :: HNil")})""".stripMargin
//  }.toList
//}
//
//// Select
//makeProductApply("QueryProjection", List("HList"), List.empty, List(
//  { case ApplyArguments(idx, typeList) =>
//      ApplyImplicits(
//        List(s"Out_$idx <: HList"),
//        List(s"""ev$idx: UnwrapAndFlattenHList.Aux[QueryProjection, ${typeList.map(s"QueryProjection[" + _ + "]").mkString("", " :: ", ":: HNil")}, QueryProjectionUnwrapper.type, Out_$idx]""")
//      )
//  }
//)).mkString("\n")
//
//makeProductApply("QueryValue", List("HList"), List.empty, List(
//  { case ApplyArguments(idx, typeList) =>
//      ApplyImplicits(
//        List(s"Out_0 <: HList"),
//        List(s"""ev0: UnwrapAndFlattenHList.Aux[QueryValue, ${typeList.map(s"QueryValue[" + _ + "]").mkString("", " :: ", ":: HNil")}, QueryValueUnwrapper.type, Out_0]""")
//      )
//  }
//)).mkString("\n")
//
//// Update
//makeProductApply("ModifyField", List("HList"), List.empty, List(
//  { case ApplyArguments(idx, typeList) =>
//    val appendTypes = typeList.map(s"ModifyField[" + _ + "]").mkString("", " :: ", ":: HNil")
//    ApplyImplicits(
//      List("Appended <: HList", "Unwrapped0 <: HList", "POut <: HList"),
//      List(
//        s"""p1: Prepend.Aux[Values, $appendTypes, Appended]""",
//        s"""un: UnwrapAndFlattenHList.Aux[ModifyField, Appended, ModifyFieldUnwrapper.type, Unwrapped0]""",
//        s"""p2: Prepend.Aux[Unwrapped0, ComparisonParams, POut]""",
//        s"""toList: ToTraversable.Aux[Appended, List, ModifyField[_ <: HList]]""")
//    )
//  }
//)).mkString("\n")
//
//// Insert
//makeProductApply("ModifyField", List("HList"), List.empty, List(
//  { case ApplyArguments(idx, typeList) =>
//    val appendTypes = typeList.map(s"ModifyField[" + _ + "]").mkString("", " :: ", ":: HNil")
//    ApplyImplicits(
//      List("Unwrapped0 <: HList"),
//      List(s"""un: UnwrapAndFlattenHList.Aux[ModifyField, $appendTypes, ModifyFieldUnwrapper.type, Unwrapped0]""")
//    )
//  }
//)).mkString("\n")
//
//// In
//makeProductApply("QueryValue", List("HList"), List.empty, List(
//  { case ApplyArguments(idx, typeList) =>
//    ApplyImplicits(
//      List(s"Unwrapped0 <: HList, Prepended0 <: HList"),
//      List(
//        s"""ev0: UnwrapAndFlattenHList.Aux[QueryValue, ${typeList.map(s"QueryValue[" + _ + "]").mkString("", " :: ", ":: HNil")}, QueryValueUnwrapper.type, Unwrapped0]""",
//        s"""ev1: Prepend.Aux[LeftType, Unwrapped0, Prepended0]"""
//      )
//    )
//  }
//)).mkString("\n")
