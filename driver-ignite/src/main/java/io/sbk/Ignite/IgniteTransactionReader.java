/**
 * Copyright (c) KMG. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package io.sbk.Ignite;

import io.sbk.api.DataType;
import io.sbk.api.Parameters;
import io.sbk.api.Reader;
import io.sbk.api.RecordTime;
import io.sbk.api.Status;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.transactions.Transaction;

import java.io.EOFException;
import java.io.IOException;

/**
 * Class for Reader.
 */
public class IgniteTransactionReader implements Reader<byte[]> {
    private final Parameters params;
    private final IgniteCache<Long, byte[]> cache;
    private final org.apache.ignite.Ignite ignite;
    private long key;
    private int cnt;

    public IgniteTransactionReader(int id, Parameters params, IgniteCache<Long, byte[]> cache,
                                   org.apache.ignite.Ignite ignite) throws IOException {
        this.params = params;
        this.cache = cache;
        this.ignite = ignite;
        this.key = Ignite.generateStartKey(id);
        this.cnt = 0;
    }

    @Override
    public byte[] read() throws EOFException, IOException {
        byte[] ret;
        ret = cache.get(key);
        if (ret != null) {
            key++;
        }
        return ret;
    }

    @Override
    public void close() throws  IOException {
    }

    @Override
    public void recordRead(DataType<byte[]> dType, Status status, RecordTime recordTime, int id)
            throws EOFException, IOException {
        final int recs;
        if (params.getRecordsPerReader() > 0 && params.getRecordsPerReader() > cnt) {
            recs = Math.min(params.getRecordsPerReader() - cnt, params.getRecordsPerSync());
        } else {
            recs =  params.getRecordsPerSync();
        }
        status.startTime = System.currentTimeMillis();
        Transaction tx = ignite.transactions().txStart();
        long startKey = key;
        Status stat = new Status();
        for (int i = 0; i < recs; i++) {
            byte[] result = cache.get(startKey++);
            if (result != null) {
                stat.bytes += result.length;
                stat.records += 1;
            }
        }
        tx.commit();
        if (stat.records == 0) {
            throw new EOFException();
        }
        status.records = stat.records;
        status.bytes = stat.bytes;
        status.endTime = System.currentTimeMillis();
        key += recs;
        cnt += recs;
        recordTime.accept(id, status.startTime, status.endTime, status.bytes, status.records);
    }


    @Override
    public void recordReadTime(DataType<byte[]> dType, Status status, RecordTime recordTime, int id)
            throws EOFException, IOException {
        final int recs;
        if (params.getRecordsPerReader() > 0 && params.getRecordsPerReader() > cnt) {
            recs = Math.min(params.getRecordsPerReader() - cnt, params.getRecordsPerSync());
        } else {
            recs =  params.getRecordsPerSync();
        }
        Transaction tx = ignite.transactions().txStart();
        long startKey = key;
        Status stat = new Status();
        for (int i = 0; i < recs; i++) {
            byte[] result = cache.get(startKey++);
            if (result != null) {
                stat.bytes += result.length;
                stat.records += 1;
                if (stat.startTime == 0) {
                    stat.startTime = dType.getTime(result);
                }
            } else {
                break;
            }
        }
        tx.commit();
        status.records = stat.records;
        status.bytes = stat.bytes;
        status.startTime = stat.startTime;
        status.endTime = System.currentTimeMillis();
        key += status.records;
        cnt += status.records;
        recordTime.accept(id, status.startTime, status.endTime, status.bytes, status.records);
    }
}