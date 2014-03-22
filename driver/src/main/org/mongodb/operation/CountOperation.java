/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.operation;

import org.mongodb.Codec;
import org.mongodb.CommandResult;
import org.mongodb.Document;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.protocol.CommandProtocol;
import org.mongodb.session.ServerConnectionProvider;
import org.mongodb.session.ServerConnectionProviderOptions;
import org.mongodb.session.Session;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class CountOperation implements Operation<Long>, AsyncOperation<Long> {
    private final DocumentCodec commandEncoder = new DocumentCodec();
    private final Codec<Document> codec;
    private final MongoNamespace namespace;
    private final Find find;

    public CountOperation(final MongoNamespace namespace, final Find find, final Codec<Document> codec) {
        this.namespace = namespace;
        this.find = find;
        this.codec = codec;
    }

    public Long execute(final Session session) {
        ReadPreferenceServerSelector serverSelector = new ReadPreferenceServerSelector(find.getReadPreference());

        ServerConnectionProvider serverConnectionProvider =
        session.createServerConnectionProvider(new ServerConnectionProviderOptions(true, serverSelector));

        return getCount(new CommandProtocol(namespace.getDatabaseName(), asDocument(), commandEncoder,
                                            codec, serverConnectionProvider.getServerDescription(),
                                            serverConnectionProvider.getConnection(), true).execute());
    }

    @Override
    public MongoFuture<Long> executeAsync(final Session session) {
        final SingleResultFuture<Long> retVal = new SingleResultFuture<Long>();
        new CommandOperation(namespace.getDatabaseName(), asDocument(), find.getReadPreference(), codec, commandEncoder)
        .executeAsync(session).register(new SingleResultCallback<CommandResult>() {
            @Override
            public void onResult(final CommandResult commandResult, final MongoException e) {
                if (e != null) {
                    retVal.init(null, e);
                } else {
                    retVal.init(getCount(commandResult), null);
                }
            }
        });
        return retVal;
    }

    private Document asDocument() {
        Document document = new Document("count", namespace.getCollectionName());

        if (find.getFilter() != null) {
            document.put("query", find.getFilter());
        }
        if (find.getLimit() > 0) {
            document.put("limit", find.getLimit());
        }
        if (find.getSkip() > 0) {
            document.put("skip", find.getSkip());
        }
        if (find.getOptions().getMaxTime(MILLISECONDS) > 0) {
            document.put("maxTimeMS", find.getOptions().getMaxTime(MILLISECONDS));
        }

        return document;
    }

    private long getCount(final CommandResult commandResult) {
        return ((Number) commandResult.getResponse().get("n")).longValue();
    }
}
