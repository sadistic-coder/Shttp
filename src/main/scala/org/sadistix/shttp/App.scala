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
        "jdbc:h2:mem://202.182.125.19/scala;DB_CLOSE_DELAY=-1",
        "root",
        "6644",
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa

  var is_exist: Int = 0

  val helloWorldService = HttpRoutes.of[IO] {
    case request@GET -> Root => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      Ok(html.hello())
    }
    case request@GET -> Root / name => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      Ok(html.hello())
    }
    case request@GET -> Root / "heart" => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      Ok(transactor.use {
        xa => sql"select love from Loves where username='admin' limit 1".query[Int].unique.transact(xa)
      }.unsafeRunSync().toString())
    }
    case request@GET -> Root / "heart" / name => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      Ok(transactor.use {
        xa => sql"select love from Loves where username=$name limit 1".query[Int].unique.transact(xa)
      }.unsafeRunSync().toString())
    }
    case request@GET -> Root / "love" => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      transactor.use {
        xa => sql"update Loves set love=love+1 where username='admin'".update.run.transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case request@GET -> Root / "love" / name => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      transactor.use {
        xa => sql"update Loves set love=love+1 where username=$name".update.run.transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case request@GET -> Root / "user" / name => {

      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
      }
      logger.info("User " + name + " is detected")
      transactor.use {
        xa => sql"insert into Loves values(0, ${name})".update.run.transact(xa)
      }.unsafeRunSync()
      Ok("123")
    }
    case request@GET -> Root / "src" / filename => {
      StaticFile.fromFile(new File("src/main/resources/"+filename), blockingEc, Some(request))
        .getOrElseF(NotFound())
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(helloWorldService)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
