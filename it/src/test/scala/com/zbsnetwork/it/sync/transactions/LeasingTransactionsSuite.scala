package com.zbsnetwork.it.sync.transactions

import com.zbsnetwork.it.api.SyncHttpApi._
import com.zbsnetwork.it.transactions.BaseTransactionSuite
import com.zbsnetwork.it.util._
import org.scalatest.CancelAfterFailure
import play.api.libs.json.Json
import com.zbsnetwork.it.sync._

class LeasingTransactionsSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val errorMessage = "Reason: Cannot lease more than own"

  test("leasing zbs decreases lessor's eff.b. and increases lessee's eff.b.; lessor pays fee") {
    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      val (balance2, eff2) = notMiner.accountBalances(secondAddress)

      val createdLeaseTxId = sender.lease(firstAddress, secondAddress, leasingAmount, leasingFee = minFee, version = v).id
      nodes.waitForHeightAriseAndTxPresent(createdLeaseTxId)

      notMiner.assertBalances(firstAddress, balance1 - minFee, eff1 - leasingAmount - minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2 + leasingAmount)
    }
  }

  test("cannot lease non-own zbs") {
    for (v <- supportedVersions) {
      val createdLeaseTxId = sender.lease(firstAddress, secondAddress, leasingAmount, leasingFee = minFee, version = v).id
      nodes.waitForHeightAriseAndTxPresent(createdLeaseTxId)

      val eff2 = notMiner.accountBalances(secondAddress)._2

      assertBadRequestAndResponse(sender.lease(secondAddress, thirdAddress, eff2 - minFee, leasingFee = minFee, version = v), errorMessage)
    }
  }

  test("can not make leasing without having enough balance") {
    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      val (balance2, eff2) = notMiner.accountBalances(secondAddress)

      //secondAddress effective balance more than general balance
      assertBadRequestAndResponse(sender.lease(secondAddress, firstAddress, balance2 + 1.zbs, minFee, version = v), errorMessage)
      nodes.waitForHeightArise()

      assertBadRequestAndResponse(sender.lease(firstAddress, secondAddress, balance1, minFee, version = v), errorMessage)
      nodes.waitForHeightArise()

      assertBadRequestAndResponse(sender.lease(firstAddress, secondAddress, balance1 - minFee / 2, minFee, version = v), errorMessage)
      nodes.waitForHeightArise()

      val newAddress = sender.createAddress()
      assertBadRequestAndResponse(sender.lease(newAddress, secondAddress, minFee, minFee, version = v), errorMessage)
      nodes.waitForHeightArise()

      notMiner.assertBalances(firstAddress, balance1, eff1)
      notMiner.assertBalances(secondAddress, balance2, eff2)
    }
  }

  test("lease cancellation reverts eff.b. changes; lessor pays fee for both lease and cancellation") {
    import com.zbsnetwork.transaction.lease.LeaseTransaction.Status._

    def getStatus(txId: String): String = {
      val r = sender.get(s"/transactions/info/$txId")
      (Json.parse(r.getResponseBody) \ "status").as[String]
    }

    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      val (balance2, eff2) = notMiner.accountBalances(secondAddress)

      val createdLeaseTxId = sender.lease(firstAddress, secondAddress, leasingAmount, minFee, version = v).id
      nodes.waitForHeightAriseAndTxPresent(createdLeaseTxId)

      notMiner.assertBalances(firstAddress, balance1 - minFee, eff1 - leasingAmount - minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2 + leasingAmount)

      val status1 = getStatus(createdLeaseTxId)
      status1 shouldBe Active

      val activeLeases = sender.activeLeases(secondAddress)
      assert(activeLeases.forall(!_.sender.contains(secondAddress)))

      val leases1 = sender.activeLeases(firstAddress)
      assert(leases1.exists(_.id == createdLeaseTxId))

      val createdCancelLeaseTxId = sender.cancelLease(firstAddress, createdLeaseTxId, minFee).id
      nodes.waitForHeightAriseAndTxPresent(createdCancelLeaseTxId)

      notMiner.assertBalances(firstAddress, balance1 - 2 * minFee, eff1 - 2 * minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2)

      val status2 = getStatus(createdLeaseTxId)
      status2 shouldBe Canceled

      val leases2 = sender.activeLeases(firstAddress)
      assert(leases2.forall(_.id != createdLeaseTxId))

      leases2.size shouldBe leases1.size - 1
    }
  }

  test("lease cancellation can be done only once") {
    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      val (balance2, eff2) = notMiner.accountBalances(secondAddress)

      val createdLeasingTxId = sender.lease(firstAddress, secondAddress, leasingAmount, minFee, version = v).id
      nodes.waitForHeightAriseAndTxPresent(createdLeasingTxId)

      notMiner.assertBalances(firstAddress, balance1 - minFee, eff1 - leasingAmount - minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2 + leasingAmount)

      val createdCancelLeaseTxId = sender.cancelLease(firstAddress, createdLeasingTxId, minFee).id
      nodes.waitForHeightAriseAndTxPresent(createdCancelLeaseTxId)

      assertBadRequestAndResponse(sender.cancelLease(firstAddress, createdLeasingTxId, minFee), "Reason: Cannot cancel already cancelled lease")

      notMiner.assertBalances(firstAddress, balance1 - 2 * minFee, eff1 - 2 * minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2)
    }
  }

  test("only sender can cancel lease transaction") {
    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      val (balance2, eff2) = notMiner.accountBalances(secondAddress)

      val createdLeaseTxId = sender.lease(firstAddress, secondAddress, leasingAmount, leasingFee = minFee, version = v).id
      nodes.waitForHeightAriseAndTxPresent(createdLeaseTxId)

      notMiner.assertBalances(firstAddress, balance1 - minFee, eff1 - leasingAmount - minFee)
      notMiner.assertBalances(secondAddress, balance2, eff2 + leasingAmount)

      assertBadRequestAndResponse(sender.cancelLease(thirdAddress, createdLeaseTxId, minFee), "LeaseTransaction was leased by other sender")
    }
  }

  test("can not make leasing to yourself") {
    for (v <- supportedVersions) {
      val (balance1, eff1) = notMiner.accountBalances(firstAddress)
      assertBadRequestAndResponse(sender.lease(firstAddress, firstAddress, balance1 + 1.zbs, minFee, v), "Transaction to yourself")
      nodes.waitForHeightArise()

      notMiner.assertBalances(firstAddress, balance1, eff1)
    }
  }
}
