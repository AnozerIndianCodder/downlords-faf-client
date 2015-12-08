package com.faforever.client.relay;

import com.faforever.client.util.SocketAddressUtil;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class ProcessNatPacketMessage extends GpgClientMessage {

  private static final int ADDRESS_INDEX = 0;
  private static final int MESSAGE_INDEX = 1;

  public ProcessNatPacketMessage(InetSocketAddress address, String message) {
    super(GpgClientCommand.PROCESS_NAT_PACKET, Arrays.asList(
        SocketAddressUtil.toString(address), message
    ));
  }

  public InetSocketAddress getAddress() {
    return SocketAddressUtil.fromString(getString(ADDRESS_INDEX));
  }

  public String getMessage() {
    return getString(MESSAGE_INDEX);
  }
}
