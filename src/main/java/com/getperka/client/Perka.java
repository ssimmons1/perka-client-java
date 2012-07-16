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

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;

/**
 * Provides access to the Perka API.
 */
public class Perka {
  private final ClientApi api;
  private final FlatPack flatpack;
  private final AuthUtils auth = new AuthUtils(this);

  public Perka() {
    this(FlatPack.create(new Configuration()
        .addTypeSource(ClientTypeSource.get())
        .withIgnoreUnresolvableTypes(true)));
  }

  protected Perka(FlatPack flatpack) {
    this.flatpack = flatpack;
    api = new ClientApi(flatpack) {
      @Override
      protected HttpURLConnection filter(HttpURLConnection conn) {
        conn = super.filter(conn);

        if (auth.getAccessToken() != null && auth.getAccessExpiration().isAfterNow()) {
          conn.setRequestProperty("Authorization", "Bearer " + auth.getAccessToken());
        }

        return conn;
      }
    };
    try {
      api.setServerBase(new URI("https://getperka.com"));
    } catch (URISyntaxException e) {
      throw new RuntimeException("Bad hard-coded value", e);
    }
  }

  /**
   * Provides access to the API methods exposed by the server.
   */
  public ClientApi api() {
    return api;
  }

  /**
   * Provides access to authorization-related functions.
   */
  public AuthUtils auth() {
    return auth;
  }

  /**
   * Provides access to {@link Merchant}-related activities.
   */
  public MerchantUtils merchants() {
    return new MerchantUtils();
  }

  Perka newInstance() {
    Perka copy = new Perka(flatpack);
    copy.api().setServerBase(api().getServerBase());
    copy.auth().copyFrom(auth());
    return copy;
  }
}
