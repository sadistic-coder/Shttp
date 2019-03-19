import java.io.File

import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import java.util.concurrent._

import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import cats.effect._
import cats.implicits._
import doobie.implicits._

// static file
import scala.concurrent.ExecutionContext

object App extends IOApp {

  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(100))
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](1000) // our connect EC 이거 접속때마다 히카리 업데이드함
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

  var count: Int = 0
  var is_exist: Int = 0

  val helloWorldService = HttpRoutes.of[IO] {
    case request@GET -> Root => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        transactor.use {
          xa => sql"insert into Loves values(0, 'las')".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
        count = 0
      }
      StaticFile
        .fromFile(new File("resource/main.html"), blockingEc, Some(request))
        .getOrElseF(NotFound())
    }
    case request@GET -> Root / "heart" => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        transactor.use {
          xa => sql"insert into Loves values(0, 'las')".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
        count = 0
      }

      Ok(transactor.use {
        xa => sql"select love from Loves where username='las' limit 1".query[Int].unique.transact(xa)
      }.unsafeRunSync().toString())
    }
    case request@GET -> Root / "love" => {
      if (is_exist == 0) {
        transactor.use {
          xa => sql"create table Loves(love integer(11), username varchar(20))".update.run.transact(xa)
        }.unsafeRunSync()
        transactor.use {
          xa => sql"insert into Loves values(0, 'las')".update.run.transact(xa)
        }.unsafeRunSync()
        is_exist += 1
        count = 0
      }
      transactor.use {
        xa => sql"update Loves set love=love+1 where username='las'".update.run.transact(xa)
      }.unsafeRunSync()
      count += 1
      Ok()
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
