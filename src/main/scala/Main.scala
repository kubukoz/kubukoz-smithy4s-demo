import demo.User
import smithy4s.schema.Schematic
import doobie.util.Write
import doobie.implicits._
import smithy4s.schema.StubSchematic
import demo.UserId
import demo.Username
import smithy4s.Timestamp
import smithy4s.schema.Field
import smithy4s.Hints
import smithy4s.Refinement
import java.time.Instant
import cats.effect.IO
import doobie.postgres.implicits._
import doobie.util.transactor
import doobie.util.Put
import cats.implicits.given
import cats.effect.IOApp
import doobie.util.Read
import doobie.util.Get
object Main extends IOApp.Simple {

  implicit val writeUser: Write[User] = User.schema
    .compile(WriteSchematic)
    .asInstanceOf[WriteOrPut.AWrite[User]]
    .write

  implicit val readUser: Read[User] = User.schema
    .compile(ReadSchematic)
    .asInstanceOf[ReadOrGet.ARead[User]]
    .read

  val user = User(
    UserId("example"),
    Username("u"),
    age = Some(42),
    lastUpdatedAt = Some(Timestamp.fromEpochSecond(0L))
  )

  val xa = transactor.Transactor.fromDriverManager[IO](
    "org.postgresql.Driver", // driver classname
    "jdbc:postgresql:postgres", // connect URL (driver-specific)
    "postgres", // user
    "p" // password
  )

  def run = {
    sql"""create table if not exists
       users(id text primary key, name text not null, age int, lastUpdatedAt timestamp)""".update.run *>
      sql"""insert into users values($user)""".update.run *>
      sql"""select * from users""".query[User].nel
  }.transact(xa).flatMap(IO.println(_))
}

enum WriteOrPut[A] {
  case AWrite(write: Write[A])
  case APut(put: Put[A])

  def contramap[B](f: B => A): WriteOrPut[B] = this match {
    case AWrite(w) => AWrite(w.contramap(f))
    case APut(p)   => APut(p.contramap(f))
  }
}

enum ReadOrGet[A] {
  case ARead(read: Read[A])
  case AGet(get: Get[A])

  def map[B](f: A => B): ReadOrGet[B] = this match {
    case ARead(r) => ARead(r.map(f))
    case AGet(g)  => AGet(g.map(f))
  }
}

import WriteOrPut._

object WriteSchematic extends StubSchematic[WriteOrPut] {
  def default[A]: WriteOrPut[A] = ???

  override def string: WriteOrPut[String] = APut(Put[String])
  override def bijection[A, B](
      f: WriteOrPut[A],
      to: A => B,
      from: B => A
  ): WriteOrPut[B] = f.contramap(from)

  override def surjection[A, B](
      f: WriteOrPut[A],
      refinement: Refinement[A, B],
      from: B => A
  ): WriteOrPut[B] = f.contramap(from)

  override def int: WriteOrPut[Int] = APut(Put[Int])

  override def timestamp: WriteOrPut[Timestamp] =
    APut(Put[Instant]).contramap(_.toInstant)

  override def withHints[A](fa: WriteOrPut[A], hints: Hints): WriteOrPut[A] = fa

  override def struct[S](fields: Vector[Field[WriteOrPut, S, _]])(
      const: Vector[Any] => S
  ): WriteOrPut[S] = WriteOrPut.AWrite {
    val writes = fields.map { case (f: Field[WriteOrPut, S, a]) =>
      val fieldPut: Field[Put, S, a] =
        f.mapK(new (smithy4s.PolyFunction[WriteOrPut, Put]) {
          def apply[A](wop: WriteOrPut[A]): Put[A] = wop match {
            case APut(p) => p
            case _       => sys.error("illegal")
          }
        })

      fieldPut
        .foldK(new Field.FolderK[Put, S, Write] {
          def onOptional[A](
              label: String,
              instance: Put[A],
              get: S => Option[A]
          ): Write[Option[A]] = Write.fromPutOption(instance)

          def onRequired[A](
              label: String,
              instance: Put[A],
              get: S => A
          ): Write[A] = Write.fromPut(instance)
        })
        .contramap(fieldPut.get)

    }

    writes.foldLeft(Write.unitComposite.contramap[S](_ => ()))(
      (_, _).contramapN(b => (b, b))
    )
  }
}

import ReadOrGet._

object ReadSchematic extends StubSchematic[ReadOrGet] {
  def default[A]: ReadOrGet[A] = ???

  override def string: ReadOrGet[String] = AGet(Get.apply)
  override def int: ReadOrGet[Int] = AGet(Get.apply)
  override def timestamp: ReadOrGet[Timestamp] = AGet(
    Get[Instant].map(Timestamp.fromInstant(_))
  )

  override def withHints[A](fa: ReadOrGet[A], hints: Hints): ReadOrGet[A] = fa

  override def bijection[A, B](
      f: ReadOrGet[A],
      to: A => B,
      from: B => A
  ): ReadOrGet[B] = f.map(to)

  override def struct[S](fields: Vector[Field[ReadOrGet, S, ?]])(
      const: Vector[Any] => S
  ): ReadOrGet[S] = ARead {
    fields
      .map { case (f: Field[ReadOrGet, S, a]) =>
        val fieldGet: Field[Get, S, a] =
          f.mapK(new (smithy4s.PolyFunction[ReadOrGet, Get]) {
            def apply[A](wop: ReadOrGet[A]): Get[A] = wop match {
              case AGet(p) => p
              case _       => sys.error("illegal")
            }
          })

        fieldGet
          .foldK(new Field.FolderK[Get, S, Read] {
            def onOptional[A](
                label: String,
                instance: Get[A],
                get: S => Option[A]
            ): Read[Option[A]] = Read.fromGetOption(instance)

            def onRequired[A](
                label: String,
                instance: Get[A],
                get: S => A
            ): Read[A] = Read.fromGet(instance)
          })
      }
      .traverse(a => a.widen[Any])
      .map(const)
  }
}
