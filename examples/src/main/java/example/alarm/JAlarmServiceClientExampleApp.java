/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.alarm;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import csw.alarm.api.javadsl.IAlarmService;
import csw.alarm.api.javadsl.JAlarmSeverity;
import csw.alarm.client.AlarmServiceFactory;
import csw.location.api.javadsl.ILocationService;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;

import java.util.concurrent.Future;

import static csw.alarm.models.Key.AlarmKey;

public class JAlarmServiceClientExampleApp {

    private ActorSystem<SpawnProtocol.Command> actorSystem;
    private ILocationService jLocationService;

    public JAlarmServiceClientExampleApp(org.apache.pekko.actor.typed.ActorSystem<SpawnProtocol.Command> actorSystem, ILocationService locationService) {
        this.actorSystem = actorSystem;
        this.jLocationService = locationService;
    }

    //#create-java-api
    // create alarm client using host and port of alarm server
    final IAlarmService jclientAPI1 = new AlarmServiceFactory().jMakeClientApi("localhost", 5227, actorSystem);

    // create alarm client using location service
    IAlarmService jclientAPI2 = new AlarmServiceFactory().jMakeClientApi(jLocationService, actorSystem);
    //#create-java-api

    //#setSeverity-java
    private final AlarmKey alarmKey = new AlarmKey(Prefix.apply(JSubsystem.NFIRAOS, "trombone"), "tromboneAxisLowLimitAlarm");
    Future<Done> doneF = jclientAPI1.setSeverity(alarmKey, JAlarmSeverity.Okay);
    //#setSeverity-java
}
