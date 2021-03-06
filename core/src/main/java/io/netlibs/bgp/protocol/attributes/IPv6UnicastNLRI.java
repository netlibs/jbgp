package io.netlibs.bgp.protocol.attributes;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.netlibs.bgp.protocol.NLRIHelper;
import io.netlibs.bgp.protocol.NetworkLayerReachabilityInformation;
import io.netlibs.ipaddr.CidrV6Address;
import io.netlibs.ipaddr.IPv6Address;
import lombok.Getter;

public class IPv6UnicastNLRI
{
  @Getter
  private NetworkLayerReachabilityInformation address;

  public IPv6UnicastNLRI(int pfxlen, byte[] data){
    this.address = new NetworkLayerReachabilityInformation(pfxlen, NLRIHelper.trimNLRI(pfxlen, data));
  }
  
  public IPv6UnicastNLRI(byte[] data){
    this.address = new NetworkLayerReachabilityInformation(data.length * 8, data);
  }
  
  public static IPv6UnicastNLRI fromCidrV6Address(CidrV6Address cidr){
    byte[] prefix = cidr.prefixAsIPv6Address().toByteArray();
    return new IPv6UnicastNLRI(cidr.mask(), prefix);
  }
  
  public static byte[] nlriTo128BIPv6(byte[] pfx)
  {
    byte[] data = new byte[16];
    // pad out to 128-bits
    if(pfx.length != 16){
      for(int i=0; i<pfx.length; i++)
        data[i] = pfx[i];
      for(int i=pfx.length;i<16;i++)
        data[i] = 0;
    } else {
      data = pfx;
    }
    return data;
  }
  
  public static byte[] bigIntegerTo128BIPv6(BigInteger pfx)
  {
    byte[] addr = new IPv6Address(pfx).toByteArray();
    return addr;
  }
  
  public InetAddress getInetAddress() throws UnknownHostException {
    byte[] data = nlriTo128BIPv6(this.address.getPrefix());
    return InetAddress.getByAddress(data);
  }
  
  public NetworkLayerReachabilityInformation getEncodedNlri(){
    return this.address;
  }
}
