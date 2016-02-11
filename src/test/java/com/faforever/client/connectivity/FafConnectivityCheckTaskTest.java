package com.faforever.client.connectivity;

import com.faforever.client.i18n.I18n;
import com.faforever.client.legacy.domain.MessageTarget;
import com.faforever.client.relay.ConnectivityStateMessage;
import com.faforever.client.relay.GpgServerMessage;
import com.faforever.client.relay.ProcessNatPacketMessage;
import com.faforever.client.relay.SendNatPacketMessage;
import com.faforever.client.remote.FafService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.util.SocketUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.util.SocketUtils.PORT_RANGE_MAX;
import static org.springframework.util.SocketUtils.PORT_RANGE_MIN;

public class FafConnectivityCheckTaskTest extends AbstractPlainJavaFxTest {

  private FafConnectivityCheckTask instance;

  @Mock
  private ExecutorService executorService;
  @Mock
  private I18n i18n;
  @Mock
  private FafService fafService;
  @Mock
  private DatagramGateway datagramGateway;
  @Captor
  private ArgumentCaptor<Consumer<GpgServerMessage>> connectivityMessageListenerCaptor;

  private DatagramSocket publicSocket;
  private int gamePort;

  @Before
  public void setUp() throws Exception {
    instance = new FafConnectivityCheckTask();
    instance.executorService = executorService;
    instance.i18n = i18n;
    instance.fafService = fafService;

    instance.setDatagramGateway(datagramGateway);

    gamePort = SocketUtils.findAvailableUdpPort();

    publicSocket = new DatagramSocket(gamePort);

    doAnswer(invocation -> {
      CompletableFuture.runAsync(invocation.getArgumentAt(0, Runnable.class));
      return null;
    }).when(executorService).execute(any(Runnable.class));
  }

  @After
  public void tearDown() throws Exception {
    IOUtils.closeQuietly(publicSocket);
  }

  @Test(expected = IllegalStateException.class)
  public void testCallPortNotSetThrowsIse() throws Exception {
    instance.call();
  }

  @Test
  public void testPublic() throws Exception {
    int playerId = 1234;
    InetSocketAddress publicAddress = new InetSocketAddress(51111);

    doAnswer(invocation -> {
      byte[] bytes = String.format("\bAre you public? %s", playerId).getBytes(UTF_8);
      DatagramPacket publicCheckPacket = new DatagramPacket(bytes, bytes.length);
      publicCheckPacket.setAddress(InetAddress.getLocalHost());
      publicCheckPacket.setPort(14123);

      @SuppressWarnings("unchecked")
      Consumer<DatagramPacket> listener = invocation.getArgumentAt(0, Consumer.class);
      listener.accept(publicCheckPacket);
      return null;
    }).when(datagramGateway).addOnPacketListener(any());

    doAnswer(invocation -> {
      ProcessNatPacketMessage processNatPacketMessage = invocation.getArgumentAt(0, ProcessNatPacketMessage.class);

      InetAddress expectedAddress = InetAddress.getLocalHost();
      InetSocketAddress actualAddress = processNatPacketMessage.getAddress();
      String message = processNatPacketMessage.getMessage();

      assertThat(message, is("Are you public? " + playerId));
      assertThat(processNatPacketMessage.getTarget(), is(MessageTarget.CONNECTIVITY));
      assertThat(actualAddress.getAddress(), is(expectedAddress));
      assertThat(actualAddress.getPort(), is(both(greaterThan(PORT_RANGE_MIN)).and(lessThan(PORT_RANGE_MAX))));

      verify(fafService).addOnMessageListener(eq(GpgServerMessage.class), connectivityMessageListenerCaptor.capture());
      connectivityMessageListenerCaptor.getValue().accept(
          new ConnectivityStateMessage(ConnectivityState.PUBLIC, publicAddress)
      );

      return null;
    }).when(fafService).sendGpgMessage(any());

    instance.setPublicPort(publicSocket.getLocalPort());

    ConnectivityStateMessage result = instance.call();
    assertThat(result.getState(), is(ConnectivityState.PUBLIC));
    assertThat(result.getSocketAddress(), is(publicAddress));
    verify(datagramGateway).removeOnPacketListener(any());
    verify(fafService).initConnectivityTest(gamePort);
  }

  @Test
  public void testStun() throws Exception {
    int playerId = 1234;
    InetSocketAddress outsideSocketAddress = new InetSocketAddress(51111);
    
    doAnswer(invocation -> {
      verify(fafService).addOnMessageListener(eq(GpgServerMessage.class), connectivityMessageListenerCaptor.capture());

      SendNatPacketMessage sendNatPacketMessage = new SendNatPacketMessage();
      sendNatPacketMessage.setTarget(MessageTarget.CONNECTIVITY);
      sendNatPacketMessage.setMessage("Hello " + playerId);

      try (DatagramSocket datagramSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0))) {
        sendNatPacketMessage.setPublicAddress((InetSocketAddress) datagramSocket.getLocalSocketAddress());
        connectivityMessageListenerCaptor.getValue().accept(sendNatPacketMessage);
        connectivityMessageListenerCaptor.getValue().accept(
            new ConnectivityStateMessage(ConnectivityState.STUN, outsideSocketAddress)
        );
      }
      return null;
    }).when(fafService).initConnectivityTest(gamePort);

    instance.setPublicPort(publicSocket.getLocalPort());

    ConnectivityStateMessage connectivityStateMessage = instance.call();
    assertThat(connectivityStateMessage.getState(), is(ConnectivityState.STUN));
    assertThat(connectivityStateMessage.getSocketAddress(), is(outsideSocketAddress));
    verify(fafService).initConnectivityTest(gamePort);
  }
}
