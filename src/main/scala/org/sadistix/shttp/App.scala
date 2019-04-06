package org.sadistix.shttp

import cats.effect._
import java.io.File
import java.lang.Exception
import java.util.concurrent.Executors

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.twirl._
import org.http4s.circe._
import java.util.concurrent._

import io.circe._
import io.circe.literal._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
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
  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  val logger = log4s.getLogger("LasLogger")

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](4)
      te <- ExecutionContexts.cachedThreadPool[IO]
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem://0.0.0.0/scala;DB_CLOSE_DELAY=-1",
        "root",
        "6644",
        ce,
        te
      )
    } yield xa

  val table_create = sql"create table Loves(love integer(11), username varchar(20) UNIQUE)".update.run
  val insert_admin = sql"insert into Loves values(0, 'admin')".update.run
  val init_db = (table_create, insert_admin).mapN(_ + _)

  val get_heart = (name: String) => sql"select love from Loves where username=$name limit 1".query[Int].unique
  val get_hearts = sql"select username, love from Loves".query[(String, String)].to[List]
  val love_user = (name: String) => sql"update Loves set love=love+1 where username=$name".update.run
  val add_user = (name: String) => sql"insert into Loves values(0, ${name})".update.run
  var is_exist: Int = 0

  val loveService = HttpRoutes.of[IO] {
    case GET -> Root => {
      Ok(html.firstPage())
    }
    case GET -> Root / "heart" => {
      val hearts = transactor.use {
        xa => get_hearts.transact(xa)
      }.unsafeRunSync()
        .asJson
        .spaces2
      Ok(hearts)
    }
    case GET -> Root / "heart" / name => {
      Ok(transactor.use {
        xa => get_heart.apply(name).transact(xa)
      }.unsafeRunSync().toString())
    }
    case GET -> Root / "love" / name => {
      transactor.use {
        xa => love_user.apply(name).transact(xa)
      }.unsafeRunSync()
      Ok()
    }
    case GET -> Root / "user" / name => {
      try {
        logger.info("User " + name + " is detected")
        transactor.use {
          xa => add_user.apply(name).transact(xa)
        }.unsafeRunSync()
        Ok(html.hello("/" + name))
      } catch {
        case _: Throwable => {
          logger.info("Already Joined User");
          Ok(html.hello("/" + name))
        }
      }
      finally {
      }
    }
    case request@GET -> Root / "src" / filename => {
      StaticFile.fromFile(new File("src/main/resources/" + filename), blockingEc, Some(request))
        .getOrElseF(NotFound())
    }
    case GET -> Root / name => {
      Ok(html.hello("/" + name))
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    transactor.use {
      xa => init_db.transact(xa)
    }.flatMap(
      (code) => {
        BlazeServerBuilder[IO]
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(loveService)
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      })
}
