/*
 * Copyright 2013-2015 Websudos, Limited.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Explicit consent must be obtained from the copyright owner, Websudos Limited before any redistribution is made.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.websudos.phantom.builder.query.db.crud

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.SpanSugar._

import com.websudos.phantom.dsl._
import com.websudos.phantom.tables._
import com.websudos.phantom.testkit._
import com.twitter.util.{ Future => TwitterFuture }
import com.websudos.util.testing._

import scala.concurrent.Future

class InsertCasTest extends PhantomCassandraTestSuite {

  implicit val s: PatienceConfiguration.Timeout = timeout(10 seconds)

  override def beforeAll(): Unit = {
    super.beforeAll()
    Primitives.insertSchema()
    TestTable.insertSchema()
    MyTest.insertSchema()
    Recipes.insertSchema()
  }

  "Standard inserts" should "create multiple database entries" in {
    val row = gen[Primitive]

    val chain = for {
      truncate <- Primitives.truncate.future()
      store <- Primitives.store(row).future()
      store <- Primitives.store(row).future()
      store <- Primitives.store(row).future()
      store <- Primitives.store(row).future()
      store <- Primitives.store(row).future()
      one <- Primitives.select.where(_.pkey eqs row.pkey).one
      multi <- Primitives.select.where(_.pkey eqs row.pkey).fetch()
      count <- Primitives.select.count.one()
    } yield (one, count, multi)

    chain successful {
      res => {
        info("The one query should return a record")
        res._1.isDefined shouldEqual true

        info("And the record should equal the inserted record")
        res._1.get shouldEqual row

        info("And the count should be present")
        res._2.isDefined shouldEqual true

        info("And it should be one after a single insertion.")
        res._2.get shouldEqual 1L

        info("And only one record should be retrieved from a range fetch")
        res._3.size shouldEqual 1
      }
    }
  }


  "Conditional inserts" should "not create duplicate database entries" in {
    val row = gen[Primitive]

    val insertion = Future.sequence(List.tabulate(5)(_ => row).map(Primitives.store(_).ifNotExists().future()))

    val chain = for {
      truncate <- Primitives.truncate.future()
      store <- insertion
      one <- Primitives.select.where(_.pkey eqs row.pkey).one
      multi <- Primitives.select.where(_.pkey eqs row.pkey).fetch()
      count <- Primitives.select.count.one()
    } yield (one, count, multi)

    chain successful {
      res => {
        info("The one query should return a record")
        res._1.isDefined shouldEqual true

        info("And the record should equal the inserted record")
        res._1.get shouldEqual row

        info("And the count should be present")
        res._2.isDefined shouldEqual true

        info("And it should be one after a single insertion.")
        res._2.get shouldEqual 1L

        info("And only one record should be retrieved from a range fetch")
        res._3.size shouldEqual 1
      }
    }
  }

  "Conditional inserts" should "not create duplicate database entries with Twitter Futures" in {
    //char is not supported
    //https://github.com/datastax/java-driver/blob/2.0/driver-core/src/main/java/com/datastax/driver/core/DataType.java
    val row = gen[Primitive]

    val insertion = TwitterFuture.collect(List.tabulate(5)(_ => row).map(Primitives.store(_).ifNotExists().execute()))

    val chain = for {
      truncate <- Primitives.truncate.execute()
      store <- insertion
      one <- Primitives.select.where(_.pkey eqs row.pkey).get
      multi <- Primitives.select.where(_.pkey eqs row.pkey).collect()
      count <- Primitives.select.count.get()
    } yield (one, count, multi)

    chain successful {
      res => {
        info("The one query should return a record")
        res._1.isDefined shouldEqual true

        info("And the record should equal the inserted record")
        res._1.get shouldEqual row

        info("And the count should be present")
        res._2.isDefined shouldEqual true

        info("And it should be one after a single insertion.")
        res._2.get shouldEqual 1L

        info("And only one record should be retrieved from a range fetch")
        res._3.size shouldEqual 1
      }
    }
  }
}
