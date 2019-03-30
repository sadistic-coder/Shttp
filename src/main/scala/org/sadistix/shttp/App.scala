package org.sadistix.shttp

import java.io.File
import java.util.concurrent.Executors

import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.twirl._
import java.util.concurrent._

import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import doobie.hikari._
import cats.effect._
import cats.implicits._
import doobie.implicits._

// static file
import scala.concurrent.ExecutionContext

import org.log4s

object App extends IOApp {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  val logger = log4s.getLogger("LasLogger")

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](2) // our connect EC 이거 접속때마다 히카리 업데이드함
      te <- ExecutionContexts.cachedThreadPool[IO] // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem://0.0.0.0/scala;DB_CLOSE_DELAY=-1",
        "root",
        "6644",
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa

  val table_create = sql"create table Loves(love integer(11), username varchar(20))".update.run
  val insert_admin = sql"insert into Loves values(0, 'admin')".update.run
  val init_db = (table_create, insert_admin).mapN(_ + _)

  val get_heart = (name: String) => sql"select love from Loves where username=$name limit 1".query[Int].unique
  val love_user = (name: String) => sql"update Loves set love=love+1 where username=$name".update.run
  val add_user = (name: String) => sql"insert into Loves values(0, ${name})".update.run

  var is_exist: Int = 0

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root => {
      Ok(html.hello(""))
    }
    case GET -> Root / name => {
      Ok(html.hello("/" + name))
    }
    case GET -> Root / "heart" => {
      Ok(transactor.use {
        xa => get_heart.apply("admin").transact(xa)
      }.unsafeRunSync().toString())
    }
    case GET -> Root / "heart" / name => {
      Ok(transactor.use {
        xa => get_heart.apply(name).transact(xa)
      }.unsafeRunSync().toString())
    }
    case GET -> Root / "love" => {
      transactor.use {
        xa => love_user.apply("admin").transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case GET -> Root / "love" / name => {
      transactor.use {
        xa => love_user.apply(name).transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case GET -> Root / "user" / name => {
      logger.info("User " + name + " is detected")
      transactor.use {
        xa => add_user.apply(name).transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case request@GET -> Root / "src" / filename => {
      StaticFile.fromFile(new File("src/main/resources/" + filename), blockingEc, Some(request))
        .getOrElseF(NotFound())
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    transactor.use {
      xa => init_db.transact(xa)
    }.flatMap(
      (code) => {
        BlazeServerBuilder[IO]
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(helloWorldService)
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      })
}
