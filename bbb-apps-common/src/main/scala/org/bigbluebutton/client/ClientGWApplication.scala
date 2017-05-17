package org.bigbluebutton.client

import akka.actor.ActorSystem
import org.bigbluebutton.client.bus._
import org.bigbluebutton.client.endpoint.redis.{AppsRedisSubscriberActor, MessageSender, RedisPublisher}
import org.bigbluebutton.client.meeting.MeetingManagerActor
import org.bigbluebutton.red5.client.messaging.IConnectionInvokerService

import scala.concurrent.duration._

class ClientGWApplication(val connectionInvokerGW: IConnectionInvokerService,
                         val oldMessageReceivedGW: OldMessageReceivedGW,
                         val msgToClientGW: MsgToClientGW) extends SystemConfiguration{

  implicit val system = ActorSystem("bbb-apps-common")
  implicit val timeout = akka.util.Timeout(3 seconds)

  println("*********** meetingManagerChannel = " + meetingManagerChannel)

  private val msgFromClientEventBus = new MsgFromClientEventBus
  private val jsonMsgToAkkaAppsBus = new JsonMsgToAkkaAppsBus
  private val msgFromAkkaAppsEventBus = new MsgFromAkkaAppsEventBus

  private val redisPublisher = new RedisPublisher(system)
  private val msgSender: MessageSender = new MessageSender(redisPublisher)

  private val messageSenderActorRef = system.actorOf(
    MessageSenderActor.props(msgSender),
    "messageSenderActor")

  jsonMsgToAkkaAppsBus.subscribe(messageSenderActorRef, toAkkaAppsJsonChannel)

  private val meetingManagerActorRef = system.actorOf(
    MeetingManagerActor.props(),
    "meetingManagerActor")

  msgFromAkkaAppsEventBus.subscribe(meetingManagerActorRef, fromAkkaAppsChannel)
  msgFromClientEventBus.subscribe(meetingManagerActorRef, fromClientChannel)

  private val receivedJsonMsgBus = new JsonMsgFromAkkaAppsBus
  private val oldMessageEventBus = new OldMessageEventBus

  private val appsRedisSubscriberActor = system.actorOf(
    AppsRedisSubscriberActor.props(receivedJsonMsgBus,oldMessageEventBus),
    "appsRedisSubscriberActor")

  private val receivedJsonMsgHdlrActor = system.actorOf(
    ReceivedJsonMsgHdlrActor.props(msgFromAkkaAppsEventBus),
    "receivedJsonMsgHdlrActor")

  receivedJsonMsgBus.subscribe(receivedJsonMsgHdlrActor, fromAkkaAppsJsonChannel)

  private val oldMessageJsonReceiverActor = system.actorOf(
    OldMessageJsonReceiverActor.props(oldMessageReceivedGW),
    "oldMessageJsonReceiverActor")

  oldMessageEventBus.subscribe(oldMessageJsonReceiverActor, fromAkkaAppsOldJsonChannel)

  def connect(connInfo: ConnInfo): Unit = {
    msgFromClientEventBus.publish(MsgFromClientBusMsg(fromClientChannel, new ConnectMsg(connInfo)))
  }

  def disconnect(connInfo: ConnInfo): Unit = {
    msgFromClientEventBus.publish(MsgFromClientBusMsg(fromClientChannel, new DisconnectMsg(connInfo)))
  }

  def handleMsgFromClient(connInfo: ConnInfo, json: String): Unit = {
    msgFromClientEventBus.publish(MsgFromClientBusMsg(fromClientChannel, new MsgFromClientMsg(connInfo, json)))
  }

  def send(channel: String, json: String): Unit = {
    jsonMsgToAkkaAppsBus.publish(JsonMsgToAkkaAppsBusMsg(toAkkaAppsJsonChannel, new JsonMsgToSendToAkkaApps(channel, json)))
  }

  def shutdown(): Unit = {
    system.terminate()
  }

}