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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;

import com.getperka.flatpack.client.impl.JsonRequestBase;
import com.getperka.flatpack.util.FlatPackTypes;

class TokenRequest extends JsonRequestBase<TokenRequest> {

  private final String payload;

  TokenRequest(Perka api, String payload) {
    super(api.api(), "POST", "/api/2/auth/token", true);
    this.payload = payload;
    header("Content-Type", "application/x-www-form-urlencoded");
  }

  @Override
  protected void writeEntity(HttpURLConnection connection) throws IOException {
    Writer writer = new OutputStreamWriter(connection.getOutputStream(), FlatPackTypes.UTF8);
    writer.write(payload);
    writer.close();
  }

}