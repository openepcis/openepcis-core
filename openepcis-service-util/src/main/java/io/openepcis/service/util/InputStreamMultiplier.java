/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.service.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InputStreamMultiplier {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InputStreamMultiplier.class);
  private final int bufferSize;
  private ExecutorService executorService = Executors.newCachedThreadPool();
  private ByteBufAllocator byteBufAllocator = PooledByteBufAllocator.DEFAULT;

  public InputStreamMultiplier(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public InputStream[] multiply(final InputStream source, int count) throws IOException {
    final PipedInputStream[] ins = new PipedInputStream[count];
    final OutputStream[] outs = new OutputStream[count];
    for (int i = 0; i < count; i++) {
      ins[i] = new PipedInputStream();
      outs[i] = new PipedOutputStream(ins[i]);
    }
    executorService.execute(new Runnable() {
      public void run() {
        try {
          copy(source, outs);
        } catch (IOException e) {
          log.error(e.getMessage(), e);
        }
      }
    });
    return ins;
  }

  protected void copy(final InputStream source, final OutputStream[] outs) throws IOException {
    final ByteBuf bufferIn = byteBufAllocator.directBuffer(bufferSize);
    int n = 0;
    try {
      while (-1 != (n = bufferIn.writeBytes(source, bufferSize))) {
        // write buffer to all output streams
        for (OutputStream out : outs) {
          bufferIn.readBytes(out, n);
          bufferIn.resetReaderIndex();
        }
        bufferIn.resetWriterIndex();
      }
    } finally {
      for (OutputStream out : outs) {
        try {
          out.close();
        } catch (IOException e) {
          log.error(e.getMessage(), e);
        }
      }
    }
  }
}
