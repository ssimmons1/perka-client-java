package com.getperka.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IntegratorTest extends Assert {

  private static final String INTEGRATOR_CLIENT_ID = "44ff7a20-cb63-11e1-9b23-0800200c9a66";
  private static final String INTEGRATOR_PASSWORD = "foobar";
  private static final String API_BASE = "https://sandbox.getperka.com/api/2";

  private Perka perka;

  /**
   * Before each test, we'll initialize our API with a base url and integrator access credentials,
   * and clear all customer data related to our integrator's managed merchant(s)
   */
  @Before
  public void before() throws URISyntaxException, IOException {
    perka = new Perka();
    perka.api().setServerBase(new URI(API_BASE));
    perka.auth().integratorLogin(UUID.fromString(INTEGRATOR_CLIENT_ID), INTEGRATOR_PASSWORD);
    perka.api().integratorDestroyDelete().execute();
  }

  /**
   * Ensures proper creation and retrieval of customers
   */
  @Test
  public void testCreateManagedCustomers() throws IOException {

    // create a new managed customer for joe@getperka.com (phone is optional)
    UserCredentials creds = new UserCredentials();
    creds.setEmail("joe@getperka.com");
    creds.setPhone("+15555555555");
    Customer customer = perka.api().integratorCustomerPost(creds).execute().getValue();
    assertEquals("joe@getperka.com", customer.getUnconfirmedEmail());

    // another request with the same email should yeild the same customer
    Customer anotherCustomer = perka.api().integratorCustomerPost(creds).execute().getValue();
    assertEquals(customer.getUuid(), anotherCustomer.getUuid());

    // another request with the same email and different phone should
    // also yield the same customer
    creds.setPhone("+17777777777");
    anotherCustomer = perka.api().integratorCustomerPost(creds).execute().getValue();
    assertEquals(customer.getUuid(), anotherCustomer.getUuid());

    // similarly, same phone and different email
    creds.setEmail("joe+another@getperka.com");
    creds.setPhone("+15555555555");
    anotherCustomer = perka.api().integratorCustomerPost(creds).execute().getValue();
    assertEquals(customer.getUuid(), anotherCustomer.getUuid());

    // another request with unique values should yield a new customer
    creds.setEmail("joe+yet_another@getperka.com");
    creds.setPhone(null);
    anotherCustomer = perka.api().integratorCustomerPost(creds).execute().getValue();
    assertNotSame(customer.getUuid(), anotherCustomer.getUuid());
  }

  /**
   * Ensures that the proper customer status can be obtained
   */
  @Test
  public void testCustomerStatus() {

  }

  /**
   * Ensures that visits can be properly created to apply punches and / or redeem rewards for a
   * customer
   */
  @Test
  public void testVisitValidations() throws Exception {
    UserCredentials creds = new UserCredentials();
    creds.setEmail("joe@getperka.com");
    Customer customer = perka.api().integratorCustomerPost(creds).execute().getValue();

    // determine the merchants associated with this integrator account.
    List<Merchant> merchants = perka.api().integratorManagedMerchantsGet().execute().getValue();

    // lets assume this integrator has only one managed merchant
    Merchant merchant = merchants.get(0);

    // By default, API endpoints DO NOT return a full object graph of data.
    // For example, the above integrator_managed_merchants_get endpoint returns
    // only the merchant with no associated location or program data. The
    // describe_type_uuid_get endpoint is an exception to this rule, and will
    // always peform a deep serialization of the entity being described. We'll
    // now describe our merchant to gain access to our location and program data.
    merchant = (Merchant) perka.api().describeTypeUuidGet(
        "merchant", merchant.getUuid()).execute().getValue();

    // The merchant's locations should now be populated
    MerchantLocation location = merchant.getMerchantLocations().get(0);

    // The program data should also be populated, so we can dig down and grab
    // a program type that we'd like to award points for
    ProgramType programType =
        merchant.getProgramTiers().get(0).getPrograms().get(0).getProgramType();

    // now we'll switch our session over to a clerk at the merchant location.
    // This will authorize our API to execute clerk enabled endpoints.
    perka = perka.auth().become(location.getUuid(), "CLERK");

    // we can now assign some loyalty punches to our new customer with a reward grant
    PunchRewardConfirmation punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(2);
    RewardGrant grant = new RewardGrant();
    grant.setCustomer(customer);
    List<AbstractRewardConfirmation> confs =
        new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    grant.setRewardConfirmations(confs);
    Visit visit = perka.api().customerRewardPut(grant).execute().getValue();

    // A new visit should have been returned describing the transaction.
    // The customer and merchant location should be what we specified
    assertEquals(customer.getUuid(), visit.getCustomer().getUuid());
    assertEquals(location.getUuid(), visit.getMerchantLocation().getUuid());

    // A new reward should have been advanced by 2 punches
    assertEquals(1, visit.getRewardAdvancements().size());
    RewardAdvancement advancement = visit.getRewardAdvancements().get(0);
    assertEquals(2, advancement.getPunchesEarned().intValue());

    // The reward should show a sum of only 2 punches since this is a new customer
    assertEquals(2, advancement.getReward().getPunchesEarned().intValue());

    // We'll now pull down the customer's full reward status
    List<Reward> rewards =
        perka.api().customerUuidGet(customer.getUuid()).execute().getValue().getRewards();

    // We should have only one non-activated, non-redeemed reward with 2 punches
    assertEquals(1, rewards.size());
    Reward reward = rewards.get(0);
    assertNull(reward.getActivatedAt());
    assertNull(reward.getRedeemedAt());
    assertEquals(2, reward.getPunchesEarned().intValue());

    // Now we'll give the customer another 9 punches, which should activate the
    // reward and make it available for redemption, and will create another
    // reward with a single punch
    punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(9);
    grant = new RewardGrant();
    grant.setCustomer(customer);
    confs = new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    grant.setRewardConfirmations(confs);
    visit = perka.api().customerRewardPut(grant).execute().getValue();

    // The customer should now one activated, and one non-activated reward, neither of which has
    // been redeemed yet
    rewards = perka.api().customerUuidGet(customer.getUuid()).execute().getValue().getRewards();
    assertEquals(2, rewards.size());
    Reward activeReward = null;
    Reward inactiveReward = null;
    for (Reward r : rewards) {
      if (r.getActivatedAt() != null) activeReward = r;
      if (r.getActivatedAt() == null) inactiveReward = r;
    }
    assertNotNull(activeReward.getActivatedAt());
    assertNull(activeReward.getRedeemedAt());
    assertEquals(10, activeReward.getPunchesEarned().intValue());

    assertNull(inactiveReward.getActivatedAt());
    assertNull(inactiveReward.getRedeemedAt());
    assertEquals(1, inactiveReward.getPunchesEarned().intValue());

    // We'll now redeem the active reward. We can also award
    // more punches in the same transaction
    punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(1);

    RedemptionRewardConfirmation rewardConfirmation = new RedemptionRewardConfirmation();
    rewardConfirmation.setReward(activeReward);

    grant = new RewardGrant();
    grant.setCustomer(customer);
    confs = new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    confs.add(rewardConfirmation);
    grant.setRewardConfirmations(confs);
    visit = perka.api().customerRewardPut(grant).execute().getValue();

    // The customer status should now show just one non-active
    // reward with 2 punches
    rewards = perka.api().customerUuidGet(customer.getUuid()).execute().getValue().getRewards();
    assertEquals(1, rewards.size());
    reward = rewards.get(0);
    assertNull(reward.getActivatedAt());
    assertNull(reward.getRedeemedAt());
    assertEquals(2, reward.getPunchesEarned().intValue());

    // If necessary, reward grants may be given an effective datetime
    // other than the current datetime. This will place the visit's
    // validation (and any resulting tier traversals) at the
    // given effective datetime.
    DateTime effectiveAt = DateTime.parse("2012-02-28T10:00:00.000Z");
    punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(9);
    grant = new RewardGrant();
    grant.setCustomer(customer);
    confs = new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    confs.add(rewardConfirmation);
    grant.setRewardConfirmations(confs);
    grant.setEffectiveAt(effectiveAt);
    visit = perka.api().customerRewardPut(grant).execute().getValue();

    // our resulting visit should have the proper validation date in the past
    assertEquals(effectiveAt.getMillis(), visit.getValidatedAt().getMillis());
  }

}
