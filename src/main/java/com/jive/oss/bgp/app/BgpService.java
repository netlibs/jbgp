package com.jive.oss.bgp.app;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import com.jive.oss.bgp.config.global.ApplicationConfiguration;
import com.jive.oss.bgp.config.nodes.impl.CapabilitiesImpl;
import com.jive.oss.bgp.config.nodes.impl.ClientConfigurationImpl;
import com.jive.oss.bgp.config.nodes.impl.PeerConfigurationImpl;
import com.jive.oss.bgp.net.ASType;
import com.jive.oss.bgp.net.AddressFamily;
import com.jive.oss.bgp.net.AddressFamilyKey;
import com.jive.oss.bgp.net.BinaryNextHop;
import com.jive.oss.bgp.net.NetworkLayerReachabilityInformation;
import com.jive.oss.bgp.net.Origin;
import com.jive.oss.bgp.net.PathSegment;
import com.jive.oss.bgp.net.PathSegmentType;
import com.jive.oss.bgp.net.RIBSide;
import com.jive.oss.bgp.net.SubsequentAddressFamily;
import com.jive.oss.bgp.net.attributes.ASPathAttribute;
import com.jive.oss.bgp.net.attributes.MultiProtocolReachableNLRI;
import com.jive.oss.bgp.net.attributes.NextHopPathAttribute;
import com.jive.oss.bgp.net.attributes.OriginPathAttribute;
import com.jive.oss.bgp.net.attributes.PathAttribute;
import com.jive.oss.bgp.net.capabilities.Capability;
import com.jive.oss.bgp.net.capabilities.MultiProtocolCapability;
import com.jive.oss.bgp.netty.fsm.FSMRegistry;
import com.jive.oss.bgp.netty.service.BGPv4Server;
import com.jive.oss.bgp.netty.service.MplsLabelNLRI;
import com.jive.oss.bgp.netty.service.RouteHandle;
import com.jive.oss.bgp.netty.service.RouteProcessor;
import com.jive.oss.bgp.rib.PeerRoutingInformationBase;
import com.jive.oss.bgp.rib.PeerRoutingInformationBaseManager;
import com.jive.oss.bgp.rib.Route;
import com.jive.oss.bgp.rib.RouteAdded;
import com.jive.oss.bgp.rib.RouteWithdrawn;
import com.jive.oss.bgp.rib.RoutingEventListener;
import com.jive.oss.bgp.rib.RoutingInformationBase;

import lombok.extern.slf4j.Slf4j;

/**
 * A higher level BGPv4 service which provides events to a registered handler.
 *
 * The BGP service itself doesn't deal with RiB handling. Instead, it passes events to the handler for the peer. A peer can have multiple
 *
 * @author theo
 *
 */

@Slf4j
public class BgpService
{

  private final Scheduler scheduler;
  private final PeerRoutingInformationBaseManager pribm = new PeerRoutingInformationBaseManager();
  private final FSMRegistry fsmRegistry;
  private final BGPv4Server serverInstance;
  private final ApplicationConfiguration app = new ApplicationConfiguration();

  public BgpService()
  {

    try
    {
      this.scheduler = new StdSchedulerFactory().getScheduler();
    }
    catch (final SchedulerException e)
    {
      throw new RuntimeException(e);
    }

    this.app.setPeerRoutingInformationBaseManager(this.pribm);
    this.fsmRegistry = new FSMRegistry(this.app, this.scheduler);
    this.serverInstance = new BGPv4Server(this.app, this.fsmRegistry);

  }

  public void start() throws Exception
  {

    try
    {
      this.scheduler.start();
    }
    catch (final SchedulerException e)
    {
      throw new RuntimeException(e);
    }

    this.fsmRegistry.createRegistry();

    if (this.serverInstance != null)
    {
      log.info("starting local BGPv4 server");
      this.serverInstance.startServer();
    }

    this.fsmRegistry.startFiniteStateMachines();

  }

  /**
   * stop the running service
   */

  public void stop()
  {

    this.fsmRegistry.stopFiniteStateMachines();

    if (this.serverInstance != null)
    {
      this.serverInstance.stopServer();
    }

    this.fsmRegistry.destroyRegistry();

  }

  public void addPeer(final InetSocketAddress addr, final int local, final int remote, final RouteProcessor proc)
  {

    // ----

    final List<PathSegment> segs = new LinkedList<>();
    segs.add(new PathSegment(ASType.AS_NUMBER_2OCTETS, PathSegmentType.AS_SEQUENCE, new int[] { 1234 }));

    final Collection<PathAttribute> pathAttributes = new LinkedList<>();
    pathAttributes.add(new ASPathAttribute(ASType.AS_NUMBER_2OCTETS, segs));
    pathAttributes.add(new NextHopPathAttribute((Inet4Address) addr.getAddress()));
    pathAttributes.add(new OriginPathAttribute(Origin.INCOMPLETE));
    pathAttributes.add(new MultiProtocolReachableNLRI(
        AddressFamily.IPv4,
        SubsequentAddressFamily.NLRI_UNICAST_WITH_MPLS_FORWARDING,
        new BinaryNextHop(new byte[] { 8, 1, 1, 1, 1, 2, 3, 4, 32 })));

    // ----

    final NetworkLayerReachabilityInformation nlri = new NetworkLayerReachabilityInformation(56, new byte[] { 0, 2, 1, 2, 3, 4, 5 });
    final Route route = new Route(
        AddressFamilyKey.IPV4_UNICAST_MPLS_FORWARDING,
        nlri,
        pathAttributes,
        new BinaryNextHop(addr.getAddress().getAddress()));

    // ----

    final PeerRoutingInformationBase prib = this.pribm.peerRoutingInformationBase("test");

    prib.allocateRoutingInformationBase(RIBSide.Local, AddressFamilyKey.IPV4_UNICAST_MPLS_FORWARDING);
    prib.allocateRoutingInformationBase(RIBSide.Remote, AddressFamilyKey.IPV4_UNICAST_MPLS_FORWARDING);

    final RoutingInformationBase rib = prib.routingBase(RIBSide.Local, AddressFamilyKey.IPV4_UNICAST_MPLS_FORWARDING);

    rib.addRoute(route);

    prib.routingBase(RIBSide.Remote, AddressFamilyKey.IPV4_UNICAST_MPLS_FORWARDING).addPerRibListener(new RoutingEventListener() {

      private final Map<NetworkLayerReachabilityInformation, RouteHandle> handles = new HashMap<>();

      @Override
      public void routeAdded(final RouteAdded event)
      {
        final Route r = event.getRoute();
        final MplsLabelNLRI nlri = new MplsLabelNLRI(r.getNlri().getPrefix());
        final RouteHandle handle = proc.add(nlri, r.getNextHop(), r.getPathAttributes());

        log.info("Route ADD[{}]: nh={}: {}", nlri.getAddress(), event.getRoute().getNextHop(), event.getRoute().getPathAttributes());

        if (handle != null)
        {
          this.handles.put(nlri.getAddress(), handle);
        }

      }

      @Override
      public void routeWithdrawn(final RouteWithdrawn event)
      {
        final Route r = event.getRoute();
        final MplsLabelNLRI nlri = new MplsLabelNLRI(r.getNlri().getPrefix());
        log.info("Route DEL: {}: {}", nlri.getAddress(), event.getRoute());
        final RouteHandle handle = this.handles.get(nlri.getAddress());

        if (handle != null)
        {
          handle.withdraw(r.getPathAttributes());
        }
        else
        {
          log.warn("failed to find route that is withdrawn: {}", r);
        }
      }

    });

    // ----

    try
    {

      final ClientConfigurationImpl clientConfig = new ClientConfigurationImpl(addr);

      final PeerConfigurationImpl config = new PeerConfigurationImpl(
          "test",
          clientConfig,
          local,
          remote,
          1,
          get(addr.getAddress()));

      final CapabilitiesImpl caps = new CapabilitiesImpl(new Capability[] {
          // new AutonomousSystem4Capability(16),
          new MultiProtocolCapability(AddressFamily.IPv4, SubsequentAddressFamily.NLRI_UNICAST_FORWARDING),
          new MultiProtocolCapability(AddressFamily.IPv4, SubsequentAddressFamily.NLRI_UNICAST_WITH_MPLS_FORWARDING),
          // new RouteRefreshCapability()
      });

      config.setCapabilities(caps);

      this.app.addPeer(config);

    }
    catch (final ConfigurationException ex)
    {
      log.error("Config Error", ex);
      throw new RuntimeException(ex);
    }

  }

  private static long get(final InetAddress a)
  {
    final byte[] b = a.getAddress();
    final long i = 16843266L;
    return i;
  }

}
