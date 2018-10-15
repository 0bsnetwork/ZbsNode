package com.zbsplatform.transaction

import com.zbsplatform.TransactionGen
import com.zbsplatform.account.PublicKeyAccount
import com.zbsplatform.features.BlockchainFeatures._
import com.zbsplatform.lagonaki.mocks.TestBlock.{create => block}
import com.zbsplatform.settings.{Constants, TestFunctionalitySettings}
import com.zbsplatform.state.diffs._
import com.zbsplatform.state.{ByteStr, EitherExt2}
import com.zbsplatform.transaction.assets.{IssueTransactionV1, SponsorFeeTransaction}
import com.zbsplatform.transaction.transfer.TransferTransactionV1
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import play.api.libs.json.Json

class SponsorFeeTransactionSpecification extends PropSpec with PropertyChecks with Matchers with TransactionGen {
  val One = 100000000L
  val NgAndSponsorshipSettings = TestFunctionalitySettings.Enabled.copy(preActivatedFeatures =
                                                                          Map(NG.id -> 0, FeeSponsorship.id -> 0, SmartAccounts.id -> 0),
                                                                        blocksForFeatureActivation = 1,
                                                                        featureCheckBlocksPeriod = 1)

  property("SponsorFee serialization roundtrip") {
    forAll(sponsorFeeGen) { transaction: SponsorFeeTransaction =>
      val recovered = SponsorFeeTransaction.parseBytes(transaction.bytes()).get
      recovered.bytes() shouldEqual transaction.bytes()
    }
  }

  property("SponsorFee serialization from TypedTransaction") {
    forAll(sponsorFeeGen) { transaction: SponsorFeeTransaction =>
      val recovered = TransactionParsers.parseBytes(transaction.bytes()).get
      recovered.bytes() shouldEqual transaction.bytes()
    }
  }

  property("JSON format validation") {
    val js = Json.parse(s"""{
 "type": 14,
 "id": "Gobt7AiyQAfduRkW8Mk3naWbzH67Zsv9rdmgRNmon1Mb",
 "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
 "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
 "fee": $One,
 "timestamp": 1520945679531,
 "proofs": [
 "3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7"
 ],
 "version": 1,
 "assetId": "9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz",
 "minSponsoredAssetFee": 100000
                       }
    """)

    val tx = SponsorFeeTransaction
      .create(
        1,
        PublicKeyAccount.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
        ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get,
        Some(100000),
        One,
        1520945679531L,
        Proofs(Seq(ByteStr.decodeBase58("3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7").get))
      )
      .right
      .get
    js shouldEqual tx.json()
  }

  property("JSON format validation for canceling sponsorship") {
    val js = Json.parse(s"""{
 "type": 14,
 "id": "HsEHzgJEkmjy2aenmbNVFrK1Na9rz4V4p7o2fY9eB9za",
 "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
 "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
 "fee": $One,
 "timestamp": 1520945679531,
 "proofs": [
 "3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7"
 ],
 "version": 1,
 "assetId": "9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz",
 "minSponsoredAssetFee": null
                       }
    """)

    val tx = SponsorFeeTransaction
      .create(
        1,
        PublicKeyAccount.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
        ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get,
        None,
        One,
        1520945679531L,
        Proofs(Seq(ByteStr.decodeBase58("3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7").get))
      )
      .right
      .get
    val tx1 = SponsorFeeTransaction
      .create(
        1,
        PublicKeyAccount.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
        ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get,
        Some(0),
        One,
        1520945679531L,
        Proofs(Seq(ByteStr.decodeBase58("3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7").get))
      )
      .right
      .get
    js shouldEqual tx.json()
    js shouldEqual tx1.json()
  }
  val invalidFee =
    Table(
      "fee",
      -1 * Constants.UnitsInZbs,
      0
    )

  property("sponsorship negative fee") {
    forAll(invalidFee) { fee: Long =>
      for {
        sender                                                                       <- accountGen
        (_, assetName, description, quantity, decimals, reissuable, iFee, timestamp) <- issueParamGen
        issue = IssueTransactionV1
          .selfSigned(sender, assetName, description, quantity, decimals, reissuable = reissuable, iFee, timestamp)
          .right
          .get
        minFee <- smallFeeGen
        assetId = issue.assetId()
      } yield SponsorFeeTransaction.selfSigned(1, sender, assetId, Some(minFee), fee, timestamp) should produce("insufficient fee")
    }
  }

  property("cancel sponsorship negative fee") {
    forAll(invalidFee) { fee: Long =>
      for {
        sender                                                                       <- accountGen
        (_, assetName, description, quantity, decimals, reissuable, iFee, timestamp) <- issueParamGen
        issue = IssueTransactionV1
          .selfSigned(sender, assetName, description, quantity, decimals, reissuable = reissuable, iFee, timestamp)
          .right
          .get
        minFee  = None
        assetId = issue.assetId()
      } yield SponsorFeeTransaction.selfSigned(1, sender, assetId, minFee, fee, timestamp) should produce("insufficient fee")
    }
  }

  property("miner receives one satoshi less than sponsor pays") {
    val setup = for {
      (acc, name, desc, quantity, decimals, reissuable, fee, ts) <- issueParamGen
      genesis = GenesisTransaction.create(acc, ENOUGH_AMT, ts).explicitGet()
      issue   = IssueTransactionV1.selfSigned(acc, name, desc, quantity, decimals, reissuable, fee, ts).explicitGet()
      minFee <- Gen.choose(1, issue.quantity)
      sponsor  = SponsorFeeTransaction.selfSigned(version = 1, acc, issue.id(), Some(minFee), One, ts).explicitGet()
      transfer = TransferTransactionV1.selfSigned(None, acc, acc, 1, ts, feeAssetId = Some(issue.id()), minFee, Array()).explicitGet()
    } yield (acc, genesis, issue, sponsor, transfer)

    forAll(setup) {
      case (acc, genesis, issue, sponsor, transfer) =>
        val b0 = block(acc, Seq(genesis, issue, sponsor))
        val b1 = block(acc, Seq(transfer))
        val b2 = block(acc, Seq.empty)

        assertNgDiffState(Seq(b0, b1), b2, NgAndSponsorshipSettings) {
          case (diff, state) =>
            state.balance(acc, None) - ENOUGH_AMT shouldBe 0
        }
    }
  }

  property("miner receives one satoshi more than sponsor pays") {
    val setup = for {
      (acc, name, desc, quantity, decimals, reissuable, fee, ts) <- issueParamGen
      genesis = GenesisTransaction.create(acc, ENOUGH_AMT, ts).explicitGet()
      issue   = IssueTransactionV1.selfSigned(acc, name, desc, quantity, decimals, reissuable, fee, ts).explicitGet()
      minFee <- Gen.choose(1000000, issue.quantity)
      sponsor   = SponsorFeeTransaction.selfSigned(version = 1, acc, issue.id(), Some(minFee), One, ts).explicitGet()
      transfer1 = TransferTransactionV1.selfSigned(None, acc, acc, 1, ts, feeAssetId = Some(issue.id()), minFee + 7, Array()).explicitGet()
      transfer2 = TransferTransactionV1.selfSigned(None, acc, acc, 1, ts, feeAssetId = Some(issue.id()), minFee + 9, Array()).explicitGet()
    } yield (acc, genesis, issue, sponsor, transfer1, transfer2)

    forAll(setup) {
      case (acc, genesis, issue, sponsor, transfer1, transfer2) =>
        val b0 = block(acc, Seq(genesis, issue, sponsor))
        val b1 = block(acc, Seq(transfer1, transfer2))
        val b2 = block(acc, Seq.empty)

        assertNgDiffState(Seq(b0, b1), b2, NgAndSponsorshipSettings) {
          case (diff, state) =>
            state.balance(acc, None) - ENOUGH_AMT shouldBe 0
        }
    }
  }

  property("sponsorship changes in the middle of a block") {
    val setup = for {
      (acc, name, desc, quantity, decimals, reissuable, fee, ts) <- issueParamGen
      genesis = GenesisTransaction.create(acc, ENOUGH_AMT, ts).explicitGet()
      issue   = IssueTransactionV1.selfSigned(acc, name, desc, quantity, decimals, reissuable, fee, ts).explicitGet()
      minFee <- Gen.choose(1, issue.quantity / 11)

      sponsor1  = SponsorFeeTransaction.selfSigned(version = 1, acc, issue.id(), Some(minFee), One, ts).explicitGet()
      transfer1 = TransferTransactionV1.selfSigned(None, acc, acc, 1, ts, Some(issue.id()), feeAmount = minFee, Array()).explicitGet()
      sponsor2  = SponsorFeeTransaction.selfSigned(version = 1, acc, issue.id(), Some(minFee * 10), One, ts).explicitGet()
      transfer2 = TransferTransactionV1.selfSigned(None, acc, acc, 1, ts, Some(issue.id()), feeAmount = minFee * 10, Array()).explicitGet()
    } yield (acc, genesis, issue, sponsor1, transfer1, sponsor2, transfer2)

    forAll(setup) {
      case (acc, genesis, issue, sponsor1, transfer1, sponsor2, transfer2) =>
        val b0 = block(acc, Seq(genesis, issue, sponsor1))
        val b1 = block(acc, Seq(transfer1, sponsor2, transfer2))
        val b2 = block(acc, Seq.empty)

        assertNgDiffState(Seq(b0, b1), b2, NgAndSponsorshipSettings) {
          case (diff, state) =>
            state.balance(acc, None) - ENOUGH_AMT shouldBe 0
        }
    }
  }
}
