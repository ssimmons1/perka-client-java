package com.getperka.client;
/*
 * #%L
 * Perka Client Library
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.getperka.client.ClientApi.CouponCampaignPost;
import com.getperka.client.ClientApi.IntegratorMerchantPost;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class IntegratorTest extends Assert {

  private static final String INTEGRATOR_CLIENT_ID = "24db6273-079e-4803-88f4-84d7c7ee5ee5";
  private static final String INTEGRATOR_PASSWORD = "testIntegrator";
  private static final String API_BASE = "https://sandbox.getperka.com/api/2";
  // private static final String API_BASE = "http://localhost";

  private static Perka perka;

  @AfterClass
  public static void afterClass() throws URISyntaxException, IOException {
    perka = new Perka();
    perka.api().setServerBase(new URI(API_BASE));
    perka.auth().integratorLogin(UUID.fromString(INTEGRATOR_CLIENT_ID), INTEGRATOR_PASSWORD);
    perka.api().integratorDestroyDelete().withIncludeMerchants(true).execute();
  }

  /**
   * Before the class, clear out the whole merchant and rebuild it.
   */
  @BeforeClass
  public static void beforeClass() throws URISyntaxException, IOException {
    perka = new Perka();
    perka.api().setServerBase(new URI(API_BASE));
    perka.auth().integratorLogin(UUID.fromString(INTEGRATOR_CLIENT_ID), INTEGRATOR_PASSWORD);
    perka.api().integratorDestroyDelete().withIncludeMerchants(true).execute();

    initializeMerchant();
  }

  private static void initializeMerchant() throws IOException {
    MerchantBuilder builder = new MerchantBuilder();
    builder.withProgram("Free coffee program").withFreeitem("free coffee")
        .withPunchesNeeded(10, 10, 10).withPurchasedItem("buy 10").withImageName("coffee");
    builder.withName("Perka Client JS Test").withVisitsNeeded(10, 30);
    // Add location
    MerchantLocation location = new MerchantLocation();
    location.setTimezone(DateTimeZone.getDefault());
    builder.add(location);
    builder.add(new MerchantLocation());
    MerchantUser admin = new MerchantUser();
    admin.setFirstName("Admin");
    admin.setLastName("User");
    builder.add(admin);
    Merchant merch = builder.build();
    merch.setMerchantState(MerchantState.PRELIMINARY);

    IntegratorMerchantPost imp = perka.api().integratorMerchantPost(merch);
    // imp.peek().withTraversalMode(com.getperka.flatpack.TraversalMode.DEEP);
    imp.execute();

    // Create coupon campaign
    CouponCampaign campaign = new CouponCampaign();
    campaign.setTitle("sweet campaign");
    List<Coupon> coupons = new ArrayList<Coupon>();
    Coupon coupon = new Coupon();

    List<CouponVisibility> cvs = new ArrayList<CouponVisibility>();
    CouponVisibility cv = new CouponVisibility();
    EverybodyTarget target = new EverybodyTarget();
    target.setMerchant(merch);
    cv.setCoupon(coupon);
    cv.setCouponTarget(target);
    cv.setMerchantLocation(merch.getMerchantLocations().get(0));
    cvs.add(cv);
    coupon.setCouponVisibilities(cvs);
    // Start now
    coupon.setLocalBeginsAt(DateTime.now(DateTimeZone.getDefault()).toLocalDateTime());
    // End one hour from now
    coupon.setLocalEndsAt(DateTime.now(DateTimeZone.getDefault()).plusHours(1).toLocalDateTime());
    coupon.setTitle("sweet deal");
    coupon.setSummary("on all the things");
    coupon.setImageName("coffee");

    coupons.add(coupon);
    campaign.setCoupons(coupons);
    CouponCampaignPost ccp = perka.api().couponCampaignPost(campaign);
    ccp.execute();
  }

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
   * Ensures that visits can be properly amended after they're created
   */
  @Test
  public void testAmendVisit() throws Exception {
    // set up a new customer
    UserCredentials creds = new UserCredentials();
    creds.setEmail("joe@getperka.com");
    Customer customer = perka.api().integratorCustomerPost(creds).execute().getValue();

    // switch over to a clerk at the first location
    List<Merchant> merchants = perka.api().integratorManagedMerchantsGet().execute().getValue();
    Merchant merchant = (Merchant) perka.api().describeTypeUuidGet(
        "merchant", merchants.get(0).getUuid()).execute().getValue();
    MerchantLocation location = merchant.getMerchantLocations().get(0);
    perka = perka.auth().become(location.getUuid(), "CLERK");

    // assign some punches
    ProgramType programType =
        merchant.getProgramTiers().get(0).getPrograms().get(0).getProgramType();
    PunchRewardConfirmation punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(1);
    RewardGrant grant = new RewardGrant();
    grant.setCustomer(customer);
    List<AbstractRewardConfirmation> confs =
        new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    grant.setRewardConfirmations(confs);
    Visit visit = perka.api().customerRewardPut(grant).execute().getValue();

    // confirm that we have one reward with a single punch
    // obtained at location_one.
    assertEquals(1, visit.getCustomer().getRewards().size());
    assertEquals(1, visit.getCustomer().getRewards().get(0).getPunchesEarned().intValue());
    assertEquals(1, visit.getRewardAdvancements().size());
    assertEquals(1, visit.getRewardAdvancements().get(0).getPunchesEarned().intValue());
    assertEquals(location.getUuid(), visit.getMerchantLocation().getUuid());

    // We can now edit the visit to change the number of punches given.
    // This operation re-writes the history of the visit, so the payload
    // must represent the new state in its entirety, even if some
    // data remains the same. Also note that the visit given MUST be
    // the customers most recent visit. You cannot currently amend any
    // visit prior to the most recent visit.
    VisitConfirmation visitConfirmation = new VisitConfirmation();
    visitConfirmation.setVisit(visit);
    punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setPunchesEarned(3);
    punchConfirmation.setProgramType(programType);
    confs = new ArrayList<AbstractRewardConfirmation>();
    confs.add(punchConfirmation);
    visitConfirmation.setRewardConfirmations(confs);
    Visit amendedVisit = perka.api().customerVisitAmendPut(visitConfirmation).execute().getValue();

    // confirm that we still have only 1 reward, but that the reward
    // now has 3 punches instead of 1
    assertEquals(1, amendedVisit.getCustomer().getRewards().size());
    assertEquals(3, amendedVisit.getCustomer().getRewards().get(0).getPunchesEarned().intValue());
    assertEquals(1, amendedVisit.getRewardAdvancements().size());
    assertEquals(3, amendedVisit.getRewardAdvancements().get(0).getPunchesEarned().intValue());
    assertEquals(location.getUuid(), amendedVisit.getMerchantLocation().getUuid());

    // In certain situations, we may also want to change the location
    // where the visit occurred. In order to do this, we need to upgrade
    // our role to a merchant user, since the clerk's access is limited
    // to the location they're assigned to
    MerchantUser manager = merchant.getMerchantUsers().get(0);
    perka.auth().integratorLogin(UUID.fromString(INTEGRATOR_CLIENT_ID), INTEGRATOR_PASSWORD);
    perka = perka.auth().become(manager);

    // swap the visit's location out for another one
    MerchantLocation locationTwo =
        merchant.getMerchantLocations().get(merchant.getMerchantLocations().size() - 1);
    assertNotSame(location.getUuid(), locationTwo.getUuid());
    amendedVisit.setMerchantLocation(locationTwo);

    // then request for the vist to be amended again. Remember, we need to
    // pass up the entire new state of the visit here, so we could change
    // the number of punches given again
    visitConfirmation.setVisit(amendedVisit);
    punchConfirmation.setPunchesEarned(4);
    Visit newAmendedVisit = perka.api().customerVisitAmendPut(visitConfirmation).execute()
        .getValue();

    // ensure that the visit was in fact moved to the new location, and that
    // the punch count was updated again
    assertEquals(locationTwo.getUuid(), newAmendedVisit.getMerchantLocation().getUuid());
    assertEquals(1, newAmendedVisit.getCustomer().getRewards().size());
    assertEquals(4, newAmendedVisit.getCustomer().getRewards().get(0).getPunchesEarned().intValue());
    assertEquals(1, newAmendedVisit.getRewardAdvancements().size());
    assertEquals(4, newAmendedVisit.getRewardAdvancements().get(0).getPunchesEarned().intValue());
  }

  /**
   * Ensures that coupons can be properly redeemed
   */
  @Test
  public void testCouponRedemption() throws Exception {
    // set up a new customer
    UserCredentials creds = new UserCredentials();
    creds.setEmail("joe@getperka.com");
    Customer customer = perka.api().integratorCustomerPost(creds).execute().getValue();

    // switch over to a clerk at the first location
    List<Merchant> merchants = perka.api().integratorManagedMerchantsGet().execute().getValue();
    Merchant merchant = (Merchant) perka.api().describeTypeUuidGet(
        "merchant", merchants.get(0).getUuid()).execute().getValue();
    MerchantLocation location = merchant.getMerchantLocations().get(0);
    perka = perka.auth().become(location.getUuid(), "CLERK");

    // each merchant location may have a set of coupon visibilites
    // enabling coupon(s) to be redeemed at that location. This
    // particular merchant has been set up with a standard coupon
    // available to any customer visiting any of their locations.
    Set<Coupon> availableCoupons = new HashSet<Coupon>();
    for (CouponVisibility vis : location.getCouponVisibilities()) {
      availableCoupons.add(vis.getCoupon());
    }
    assertEquals(1, availableCoupons.size());
    Coupon coupon = availableCoupons.iterator().next();

    // we can now specify that this coupon should be redeemed when
    // validating a visit. We can also pass along some punches
    // earned to be recoreded in the same transaction
    ProgramType programType =
        merchant.getProgramTiers().get(0).getPrograms().get(0).getProgramType();

    RedemptionCouponConfirmation couponConfirmation = new RedemptionCouponConfirmation();
    couponConfirmation.setCoupon(coupon);

    PunchRewardConfirmation punchConfirmation = new PunchRewardConfirmation();
    punchConfirmation.setProgramType(programType);
    punchConfirmation.setPunchesEarned(1);

    List<AbstractRewardConfirmation> confs = new ArrayList<AbstractRewardConfirmation>();
    confs.add(couponConfirmation);
    confs.add(punchConfirmation);

    RewardGrant grant = new RewardGrant();
    grant.setCustomer(customer);
    grant.setRewardConfirmations(confs);
    Visit visit = perka.api().customerRewardPut(grant).execute().getValue();

    // confirm that the coupon has been redeemed by
    // looking for an appropriate coupon redemption
    // within the resulting visit
    assertEquals(1, visit.getCouponRedemptions().size());
    CouponRedemption redemption = visit.getCouponRedemptions().get(0);
    assertEquals(coupon.getUuid(), redemption.getCoupon().getUuid());

    // our punches should also be present in the visit
    assertEquals(location.getUuid(), visit.getMerchantLocation().getUuid());
    assertEquals(1, visit.getCustomer().getRewards().size());
    assertEquals(1, visit.getCustomer().getRewards().get(0).getPunchesEarned().intValue());
    assertEquals(1, visit.getRewardAdvancements().size());
    assertEquals(1, visit.getRewardAdvancements().get(0).getPunchesEarned().intValue());
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
  public void testCustomerStatus() throws Exception {
    // set up a new customer
    UserCredentials creds = new UserCredentials();
    creds.setEmail("joe@getperka.com");
    Customer customer = perka.api().integratorCustomerPost(creds).execute().getValue();

    // switch over to a clerk at the first location
    List<Merchant> merchants = perka.api().integratorManagedMerchantsGet().execute().getValue();
    Merchant merchant = (Merchant) perka.api().describeTypeUuidGet(
        "merchant", merchants.get(0).getUuid()).execute().getValue();
    MerchantLocation location = merchant.getMerchantLocations().get(0);
    perka = perka.auth().become(location.getUuid(), "CLERK");

    // fetch our customer. The customer_uuid_get endpoint will
    // populate the resulting customer with reward, tier_traversal, and
    // available coupon information
    customer = perka.api().customerUuidGet(customer.getUuid()).execute().getValue();

    // since this customer doesn't have any visits yet, there should be
    // no tier_traversal or reward information
    assertNull(customer.getTierTraversals());
    assertNull(customer.getRewards());

    // let's go ahaead and create a visit
    ProgramType programType =
        merchant.getProgramTiers().get(0).getPrograms().get(0).getProgramType();
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

    // Note that since the most recent tierTraversal can be expected in the
    // response, the visit can be used to check the customer's current status
    // at the merchant. In this case, our customer should be in the
    // lowest 'local' tier. We do a simple name comparison here, but if you
    // need to verify that a customer belongs to a particular tier, the tier
    // should be compared against one of those returned from a
    // describe_entity_get(merchant) request.
    assertEquals(1, visit.getCustomer().getTierTraversals().size());
    assertEquals("local", visit.getCustomer().getTierTraversals().get(0).getProgramTier().getName());

    // We'll make another round trip to the server to ensure we can
    // now access the customer's most recent tier traversal for the
    // merchant associated with the current session
    customer = perka.api().customerUuidGet(customer.getUuid()).execute().getValue();
    assertEquals(1, customer.getTierTraversals().size());
    assertEquals("local", customer.getTierTraversals().get(0).getProgramTier().getName());
    assertEquals(merchant.getUuid(),
        customer.getTierTraversals().get(0).getProgramTier().getMerchant().getUuid());
  }

  /**
   * Ensures entities can be properly annotated with arbitray JSON data
   */
  @Test
  public void testEntityAnnotation() throws Exception {

    // first we'll grab a reference to one of our managed merchants
    List<Merchant> merchants = perka.api().integratorManagedMerchantsGet().execute().getValue();
    Merchant merchant = merchants.get(0);

    // then apply an arbitrary annotation to the merchant
    JsonElement json = new JsonParser().parse("{'foo':'bar'}");
    EntityAnnotation entityAnnotation = new EntityAnnotation();
    entityAnnotation.setEntity(merchant);
    entityAnnotation.setAnnotation(json);
    perka.api().annotationPut(entityAnnotation).execute().getValue();

    // which can be retreived at any time
    entityAnnotation = perka.api().annotationTypeUuidGet(
        "merchant", merchant.getUuid()).execute().getValue();
    assertEquals(json, entityAnnotation.getAnnotation());

    // now we'll update our annotation to a new value
    json = new JsonParser().parse("{'bar':'baz'}");
    entityAnnotation.setAnnotation(json);
    entityAnnotation = perka.api().annotationPut(entityAnnotation).execute().getValue();

    // and verify the update
    assertEquals(json, entityAnnotation.getAnnotation());
  }

  /**
   * Ensures that visits can be properly created to apply punches and / or redeem rewards for a
   * customer
   */
  @Test
  public void testVisitValidations() throws Exception {

    // we'll first create a new customer
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
