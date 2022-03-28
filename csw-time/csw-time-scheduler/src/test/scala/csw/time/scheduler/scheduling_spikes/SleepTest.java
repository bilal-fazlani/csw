/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.time.scheduler.scheduling_spikes;

public class SleepTest {
    public static void main(String[] args) throws InterruptedException {
        while (true) {
            long start = System.nanoTime();
            Thread.sleep(0, 10000);
            System.out.println(System.nanoTime() - start + "---" + (System.nanoTime() - start) / 1000000);
        }
    }
}
