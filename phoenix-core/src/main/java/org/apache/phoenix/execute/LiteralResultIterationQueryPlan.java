/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.execute;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.phoenix.compile.GroupByCompiler.GroupBy;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.RowProjector;
import org.apache.phoenix.compile.StatementContext;
import org.apache.phoenix.iterate.ParallelIteratorFactory;
import org.apache.phoenix.iterate.ParallelScanGrouper;
import org.apache.phoenix.iterate.ResultIterator;
import org.apache.phoenix.iterate.SequenceResultIterator;
import org.apache.phoenix.parse.FilterableStatement;
import org.apache.phoenix.query.KeyRange;
import org.apache.phoenix.schema.TableRef;
import org.apache.phoenix.schema.tuple.SingleKeyValueTuple;
import org.apache.phoenix.schema.tuple.Tuple;

public class LiteralResultIterationQueryPlan extends BaseQueryPlan {
    protected final Iterator<Tuple> tupleIterator;

    public LiteralResultIterationQueryPlan(StatementContext context, 
            FilterableStatement statement, TableRef tableRef, RowProjector projection, 
            Integer limit, OrderBy orderBy, ParallelIteratorFactory parallelIteratorFactory) {
        this(Collections.<Tuple> singletonList(new SingleKeyValueTuple(KeyValue.LOWESTKEY)).iterator(), 
                context, statement, tableRef, projection, limit, orderBy, parallelIteratorFactory);
    }

    public LiteralResultIterationQueryPlan(Iterator<Tuple> tupleIterator, StatementContext context, 
            FilterableStatement statement, TableRef tableRef, RowProjector projection, 
            Integer limit, OrderBy orderBy, ParallelIteratorFactory parallelIteratorFactory) {
        super(context, statement, tableRef, projection, context.getBindManager().getParameterMetaData(), limit, orderBy, GroupBy.EMPTY_GROUP_BY, parallelIteratorFactory);
        this.tupleIterator = tupleIterator;
    }

    @Override
    public List<KeyRange> getSplits() {
        return Collections.emptyList();
    }

    @Override
    public List<List<Scan>> getScans() {
        return Collections.emptyList();
    }

    @Override
    public boolean useRoundRobinIterator() throws SQLException {
        return false;
    }

    @Override
    protected ResultIterator newIterator(ParallelScanGrouper scanGrouper)
            throws SQLException {
        ResultIterator scanner = new ResultIterator() {
            private boolean closed = false;
            private int count = 0;

            @Override
            public void close() throws SQLException {
                this.closed = true;;
            }

            @Override
            public Tuple next() throws SQLException {
                if (!this.closed 
                        && (limit == null || count++ < limit)
                        && tupleIterator.hasNext()) {
                    return tupleIterator.next();
                }
                return null;
            }

            @Override
            public void explain(List<String> planSteps) {
            }
            
        };
        
        if (context.getSequenceManager().getSequenceCount() > 0) {
            scanner = new SequenceResultIterator(scanner, context.getSequenceManager());
        }
        
        return scanner;
    }

    @Override
    public QueryPlan limit(Integer limit) {
        if (limit == this.limit || (limit != null && limit.equals(this.limit)))
            return this;
        
        return new LiteralResultIterationQueryPlan(this.tupleIterator, this.context, this.statement, this.tableRef, 
                this.projection, limit, this.orderBy, this.parallelIteratorFactory);
    }

}
