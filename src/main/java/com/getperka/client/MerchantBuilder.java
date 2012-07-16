/*
 * #%L
 * Perka Client Library
 * %%
 * Copyright (C) 2012 Perka Inc.
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
package com.getperka.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A utility class for creating new Merchant objects and populating its various datastructures. This
 * helper assumes that you intend to build a standard three-tier program (locals, regulars, and
 * VIPs) that differ chiefly in the number of punches required to fill a punchcard at the given
 * level. The Merchant returned from this builder may be further customized to meet the needs of the
 * specific offering.
 * 
 * @see MerchantUtils#newMerchant()
 */
public class MerchantBuilder {

  public class OfferBuilder {
    private final Program localProgram = new Program();
    private final Program regularProgram = new Program();
    private final Program vipProgram = new Program();
    private final List<Program> all = Arrays.asList(localProgram, regularProgram, vipProgram);

    OfferBuilder(String programName) {
      localProgram.setProgramTier(localTier);
      regularProgram.setProgramTier(regularTier);
      vipProgram.setProgramTier(vipTier);

      localTier.setPrograms(Collections.singletonList(localProgram));
      regularTier.setPrograms(Collections.singletonList(regularProgram));
      vipTier.setPrograms(Collections.singletonList(vipProgram));

      ProgramType type = new ProgramType();
      type.setName(programName);
      for (Program p : all) {
        p.setProgramType(type);
      }
    }

    /**
     * Returns the {@link MerchantBuilder} used to construct the OfferBuilder.
     */
    public MerchantBuilder finishOffer() {
      return MerchantBuilder.this;
    }

    /**
     * Sets the reward to receive after filling a punchcard across all tiers.
     */
    public OfferBuilder withFreeitem(String item) {
      for (Program p : all) {
        p.setFreeItem(item);
      }
      return this;
    }

    /**
     * Sets the icon name displayed along with the program.
     * 
     * @see ClientApi#assetManifestPerksGet()
     */
    public OfferBuilder withImageName(String name) {
      for (Program p : all) {
        p.setImageName(name);
      }
      return this;
    }

    /**
     * Sets the number of punches needed at each loyalty tier to fill a punchard.
     */
    public OfferBuilder withPunchesNeeded(int locals, int regulars, int vips) {
      localProgram.setPunchesNeeded(locals);
      regularProgram.setPunchesNeeded(regulars);
      vipProgram.setPunchesNeeded(vips);
      return this;
    }

    /**
     * Sets the item that the customer must purchase to receive a punch.
     */
    public OfferBuilder withPurchasedItem(String item) {
      for (Program p : all) {
        p.setPurchasedItem(item);
      }
      return this;
    }

    /**
     * Provides additional terms and conditions or restrictions specific to the program.
     */
    public OfferBuilder withTerms(String name) {
      for (Program p : all) {
        p.setTerms(name);
      }
      return this;
    }
  }

  private Merchant merchant;
  private ProgramTier localTier;
  private ProgramTier regularTier;
  private ProgramTier vipTier;

  MerchantBuilder() {
    merchant = new Merchant();
    merchant.setMerchantLocations(new ArrayList<MerchantLocation>());
    merchant.setMerchantUsers(new ArrayList<MerchantUser>());
    merchant.setMerchantState(MerchantState.TRIAL);

    localTier = new ProgramTier();
    localTier.setMerchant(merchant);
    localTier.setName("local");
    localTier.setVisitsNeeded(1);

    regularTier = new ProgramTier();
    regularTier.setMerchant(merchant);
    regularTier.setName("regular");

    vipTier = new ProgramTier();
    vipTier.setMerchant(merchant);
    vipTier.setName("fixture");

    merchant.setProgramTiers(Arrays.asList(localTier, regularTier, vipTier));
  }

  /**
   * Add a new location to the Merchant.
   */
  public MerchantBuilder add(MerchantLocation location) {
    location.setMerchant(merchant);
    merchant.getMerchantLocations().add(location);
    return this;
  }

  /**
   * Add user credentials that can log into the merchant dashboard.
   */
  public MerchantBuilder add(MerchantUser user) {
    user.setMerchant(merchant);
    merchant.getMerchantUsers().add(user);
    return this;
  }

  /**
   * Return the Merchant. Once this method is called, the MerchantBuilder should be discarded.
   */
  public Merchant build() {
    Merchant toReturn = merchant;
    merchant = null;
    return toReturn;
  }

  /**
   * Add an additional perk description across all tiers.
   * <p>
   * For example a Taquería might offer {@code Free salsa for Perka members} at all levels.
   * 
   * @see ProgramTier#setAdditionalPerks(String)
   */
  public MerchantBuilder withAdditionalPerks(String perks) {
    localTier.setAdditionalPerks(perks);
    regularTier.setAdditionalPerks(perks);
    vipTier.setAdditionalPerks(perks);
    return this;
  }

  /**
   * Sets an advertising headline.
   * <p>
   * This usually takes the form {@code Free Something is Awesome} for many Perka programs.
   */
  public MerchantBuilder withHeadline(String headline) {
    merchant.setHeadline(headline);
    return this;
  }

  /**
   * Sets the merchant's displayed name.
   */
  public MerchantBuilder withName(String name) {
    merchant.setName(name);
    return this;
  }

  /**
   * Creates a helper method for configuring the {@link Program}, {@link ProgramTier}, and
   * {@link ProgramType} associated with a loyalty program.
   * 
   * @param programName A short, usually one-word, description of the product being offered, e.g.
   *          {@code coffee} or {@code sandwiches}
   * @return a builder for customizing the offering
   */
  public OfferBuilder withProgram(String programName) {
    return new OfferBuilder(programName);
  }

  /**
   * Optionally sets the expiration time to remove phantom checkins.
   */
  public MerchantBuilder withVisitExpiration(int minutes) {
    merchant.setVisitExpirationMinutes(minutes);
    return this;
  }

  /**
   * Sets the number of visits needed for a customer to advance to a higher loyalty tier.
   */
  public MerchantBuilder withVisitsNeeded(int regulars, int vips) {
    regularTier.setVisitsNeeded(regulars);
    vipTier.setVisitsNeeded(vips);
    return this;
  }
}
