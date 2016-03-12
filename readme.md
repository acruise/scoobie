

[![Join the chat at https://gitter.im/Jacoby6000/Scala-SQL-AST](https://badges.gitter.im/Jacoby6000/Scala-SQL-AST.svg)](https://gitter.im/Jacoby6000/Scala-SQL-AST?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

### Querying with Doobie, without raw sql

The goal of this project is to produce an alternative to writing SQL queries for use with Doobie.

As it stands now, there is a quick 'n dirty SQL DSL, implemented with a lightweight AST. Other DSLs may be created in the future.

### The Sql DSL

Below is a sample query that somebody may wants to write. The query below is perfectly valid; try it out!

```scala
import com.github.jacoby6000.query.ast._
import com.github.jacoby6000.query.interpreter
import com.github.jacoby6000.query.dsl.sql._

val q =
  select (
    p"foo" ++ 10 as "woozle",
    `*`
  ) from "bar" leftOuterJoin "baz" on (
    p"bar.id" === p"baz.barId"
  ) innerJoin "biz" on (
    p"biz.id" === p"bar.bizId"
  ) where (
    p"biz.name" === "LightSaber" and
    p"biz.age" > 27
  ) orderBy p"biz.age".desc groupBy p"baz.worth".asc

interpreter.interpretPSql(q.query) // Print the Postgres sql string that would be created by this query
```

The sql output of this would be

```sql
SELECT
    foo + 10 AS woozle,
    *
FROM
    bar
LEFT OUTER JOIN
    baz
        ON bar.id = baz.barId
INNER JOIN
    biz
        ON biz.id = bar.bizId
WHERE
    biz.name = “LightSaber”
    AND  biz.age > 27
ORDER BY
    biz.age DESC
GROUP BY
    baz.worth ASC
```

As a proof of concept, here are some examples translated over from the book of doobie

```scala
import com.github.jacoby6000.query.ast._
import com.github.jacoby6000.query.interpreter
import com.github.jacoby6000.query.dsl.sql._
import com.github.jacoby6000.query.dsl.doobie._
import doobie.imports._
import shapeless.HNil

import scalaz.concurrent.Task

case class Country(code: String, name: String, pop: Int, gnp: Option[Double])

val xa = DriverManagerTransactor[Task](
  "org.postgresql.Driver", "jdbc:postgresql:world", "postgres", ""
)

val baseQuery =
  select(
    p"code",
    p"name",
    p"population",
    p"gnp"
  ) from p"country"

def biggerThan(n: Int) =
  (baseQuery where p"population" > `?`)
    .query[Country]
    .prepare(n)
    .list

val biggerThanRun = biggerThan(150000000).transact(xa).run
    /*List(
        Country(BRA,Brazil,170115000,Some(776739.0))
        Country(IDN,Indonesia,212107000,Some(84982.0))
        Country(IND,India,1013662000,Some(447114.0))
        Country(CHN,China,1277558000,Some(982268.0))
        Country(PAK,Pakistan,156483000,Some(61289.0))
        Country(USA,United States,278357000,Some(8510700.0))
      )*/

def populationIn(r: Range) =
  (baseQuery where (
    p"population" >= `?` and
    p"population" <= `?`
  )).query[Country]
    .prepare((r.min, r.max))
    .list

val populationInRun = populationIn(1000 to 10000).transact(xa).run
    /*List(
        Country(BRA,Brazil,170115000,Some(776739.0)),
        Country(PAK,Pakistan,156483000,Some(61289.0))
      )*/
```

And a more complicated example

```scala
def joined: ConnectionIO[List[ComplimentaryCountries]] =
  (select(
    p"c1.code",
    p"c1.name",
    p"c2.code",
    p"c2.name"
  ) from (
    p"country" as "c1"
  ) leftOuterJoin (
    p"country" as "c2"
  ) on (
    func"reverse"(p"c1.code") === p"c2.code"
  ) where (
    (p"c2.code" !== `null`) and
    (p"c2.name" !== p"c1.name")
  )).query[ComplimentaryCountries]
    .prepare
    .list

val joinResult = joined.transact(xa).run
    /*List(
        ComplimentaryCountries(PSE,Palestine,ESP,Spain),
        ComplimentaryCountries(YUG,Yugoslavia,GUY,Guyana),
        ComplimentaryCountries(ESP,Spain,PSE,Palestine),
        ComplimentaryCountries(SUR,Suriname,RUS,Russian Federation),
        ComplimentaryCountries(RUS,Russian Federation,SUR,Suriname),
        ComplimentaryCountries(VUT,Vanuatu,TUV,Tuvalu),
        ComplimentaryCountries(TUV,Tuvalu,VUT,Vanuatu),
        ComplimentaryCountries(GUY,Guyana,YUG,Yugoslavia)
    )*/

```