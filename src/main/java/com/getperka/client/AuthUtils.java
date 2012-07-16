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

import java.io.IOException;
import java.net.URLEncoder;
import java.util.UUID;

import org.joda.time.DateTime;

import com.getperka.flatpack.client.StatusCodeException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Authentication-related utility methods for interacting with Perka.
 */
public class AuthUtils {
  private final Perka api;
  private String accessToken;
  private DateTime accessExpiration;
  private String refreshToken;
  private UUID userUuid;

  AuthUtils(Perka api) {
    this.api = api;
  }

  /**
   * Shorthand method for {@link #become(UUID, String)}.
   */
  public Perka become(AbstractUser user) throws IOException {
    return become(user.getUuid(), user.getRole());
  }

  /**
   * Attempts to switch to the requested user and role. If successful, this method returns a new
   * PerkaApi instance configured with the user's access credentials.
   * 
   * @param user the UUID of the requested {@link AbstractUser}
   * @param role the role ofthe requested {@link AbstractUser}
   * @return A new instance of {@link Perka}
   */
  public Perka become(UUID user, String role) throws IOException {
    String payload = "grant_type=client_credentials" +
      "&scope=" + URLEncoder.encode(role, "UTF8") + ":" + user;

    Perka copy = api.newInstance();
    copy.auth().executeTokenRequest(payload);
    return copy;
  }

  public DateTime getAccessExpiration() {
    return accessExpiration;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public UUID getUserUuid() {
    return userUuid;
  }

  /**
   * Obtain an access key and refresh token.
   * 
   * @param integratorId the Perka-assigned UUID for your integration
   * @param integratorSecret the password for the integrator account
   */
  public void integratorLogin(UUID integratorId, String integratorSecret) throws IOException {
    String payload = "grant_type=password" +
      "&username=" + integratorId +
      "&client_id=" + integratorId +
      "&password=" + URLEncoder.encode(integratorSecret, "UTF8") +
      "&scope=INTEGRATOR";

    executeTokenRequest(payload);
  }

  /**
   * Obtain a new access key using integrator credentials and a refresh token.
   */
  public void refreshToken(UUID integratorId, String integratorSecret, String refreshToken)
      throws IOException {
    String payload = "grant_type=refresh_token" +
      "&client_id=" + integratorId +
      "&client_secret=" + URLEncoder.encode(integratorSecret, "UTF8") +
      "&refresh_token=" + refreshToken;

    executeTokenRequest(payload);
  }

  public void setAccessExpiration(DateTime accessExpiration) {
    this.accessExpiration = accessExpiration;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public void setUserUuid(UUID userUuid) {
    this.userUuid = userUuid;
  }

  void copyFrom(AuthUtils other) {
    accessExpiration = other.accessExpiration;
    accessToken = other.accessToken;
    refreshToken = other.refreshToken;
    userUuid = other.userUuid;
  }

  private void executeTokenRequest(String payload)
      throws IOException, StatusCodeException {
    TokenRequest req = new TokenRequest(api, payload);
    JsonElement elt = req.execute();
    if (elt != null && elt.isJsonObject()) {
      JsonObject obj = elt.getAsJsonObject();

      int statusCode = obj.get("status_code").getAsInt();
      if (200 != statusCode) {
        StatusCodeException sce = new StatusCodeException(statusCode, null);
        sce.setJsonElement(obj);
        throw sce;
      }

      String expires = getOrNull(obj, "expires_in");
      if (expires != null) {
        setAccessExpiration(DateTime.now().plusSeconds(Integer.valueOf(expires)));
      }
      setAccessToken(getOrNull(obj, "access_token"));
      setRefreshToken(getOrNull(obj, "refresh_token"));
      String uuid = getOrNull(obj, "uuid");
      if (uuid != null) {
        setUserUuid(UUID.fromString(uuid));
      }
    }
  }

  private String getOrNull(JsonObject obj, String key) {
    return obj.has(key) ? obj.get(key).getAsString() : null;
  }
}
