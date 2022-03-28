/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.javadsl;

import csw.logging.client.LogCommand;

public class JLogCommand {
    public static final LogCommand LogTrace = LogCommand.LogTrace$.MODULE$;
    public static final LogCommand LogDebug = LogCommand.LogDebug$.MODULE$;
    public static final LogCommand LogInfo = LogCommand.LogInfo$.MODULE$;
    public static final LogCommand LogWarn = LogCommand.LogWarn$.MODULE$;
    public static final LogCommand LogError = LogCommand.LogError$.MODULE$;
    public static final LogCommand LogFatal = LogCommand.LogFatal$.MODULE$;
}


