package io.pivotal.pde.gemfire.monitor;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gemstone.gemfire.management.CacheServerMXBean;
import com.gemstone.gemfire.management.DistributedSystemMXBean;
import com.gemstone.gemfire.management.MemberMXBean;

public class GemFireMonitor extends Thread {

	private static Logger log = LoggerFactory.getLogger(GemFireMonitor.class);

	private Logger metricLog = null;
	private JMXServiceURL jmxURL;
	private AtomicBoolean running = null;
	private HashMap<String, Serializable> jmxEnv = null;

	public GemFireMonitor(String jmxHost, int jmxPort, String jmxUserName, String jmxPassword) {
		this.setDaemon(false);
		this.running = new AtomicBoolean(false);
		this.metricLog = LoggerFactory.getLogger("gemfire-monitor");

		try {
			this.jmxURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi");
			if (jmxUserName != null) {
				jmxEnv = new HashMap<String, Serializable>();
				jmxEnv.put(JMXConnector.CREDENTIALS, new String[] { jmxUserName, String.valueOf(jmxPassword) });
			}
		} catch (MalformedURLException mux) {
			log.error("GemFire monitor instantiation failed", mux);
			throw new RuntimeException("monitor instantiation failed");
		} catch (IOException iox) {
			log.error("GemFire monitor instantiation failed", iox);
			throw new RuntimeException("monitor instantiation failed");
		}
	}

	@Override
	public void run() {
		running.set(true);
		while (running.get()) {
			JMXConnector jmxc = null;
			try {
				jmxc = JMXConnectorFactory.connect(jmxURL, jmxEnv);
				MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
				ObjectName dsOname = new ObjectName("GemFire:service=System,type=Distributed");
				DistributedSystemMXBean distributedSystemBean = JMX.newMXBeanProxy(mbsc, dsOname,
						DistributedSystemMXBean.class);

				logMetric(dsOname.toString(), "MemberCount", distributedSystemBean.getMemberCount());
				logMetric(dsOname.toString(), "JVMPauses", distributedSystemBean.getJVMPauses());
				logMetric(dsOname.toString(), "NumClients", distributedSystemBean.getNumClients());
				logMetric(dsOname.toString(), "TotalRegionEntryCount",
						distributedSystemBean.getTotalRegionEntryCount());

				ObjectName[] memberONames = distributedSystemBean.listMemberObjectNames();
				for (ObjectName memberOName : memberONames) {
					MemberMXBean memberMBean = JMX.newMXBeanProxy(mbsc, memberOName, MemberMXBean.class);

					int numThreads = memberMBean.getNumThreads();
					logMetric(memberOName.toString(), "NumThreads", numThreads);
					logMetric(memberOName.toString(), "HostCpuUsage", memberMBean.getHostCpuUsage());
					logMetric(memberOName.toString(), "FreeHeap", String.format("%dM", memberMBean.getFreeMemory()));

					if (numThreads > 2000) {
						log.warn("member " + memberMBean.getName() + " (pid " + memberMBean.getProcessId() + " ) on " + memberMBean.getHost() +  " has HIGH THREAD COUNT: " + numThreads + " thread list follows");
						for (String tname : memberMBean.fetchJvmThreads())
							logMetric(memberOName.toString(), "ThreadName", tname);
					}
				}

				ObjectName[] cacheServerONames = distributedSystemBean.listCacheServerObjectNames();
				for (ObjectName cacheServerOName : cacheServerONames) {
					CacheServerMXBean csBean = JMX.newMBeanProxy(mbsc, cacheServerOName, CacheServerMXBean.class);
					logMetric(cacheServerOName.toString(), "ClientCount", csBean.getCurrentClients());
					logMetric(cacheServerOName.toString(), "ClientConnectionCount", csBean.getClientConnectionCount());
					logMetric(cacheServerOName.toString(), "MaxClientConnectionCount", csBean.getMaxConnections());
					logMetric(cacheServerOName.toString(), "ConnectionThreads", csBean.getConnectionThreads());
					logMetric(cacheServerOName.toString(), "MaxConnectionThreads", csBean.getMaxThreads());
				}
			} catch (IOException iox) {
				log.error("GemFire monitor failure", iox);
			} catch (MalformedObjectNameException mfx) {
				log.error("Internal error in GemFire monitor - will shut down", mfx);
				running.set(false); // will keep the shutdown method from
									// waiting
				break; // BREAK
			} finally {
				try {
					jmxc.close();
				} catch (IOException iox) {
					log.error("error closing JMX connector", iox);
				}
			}

			try {
				Thread.sleep(20000);
			} catch (InterruptedException ix) {
				// OK
			}
		}
	}

	private void logMetric(String subject, String metric, Object value) {
		metricLog.info(String.format("%s|%s|%s", subject, metric, value));
	}

	public void shutdown() {
		if (running.compareAndSet(true, false)) {
			this.interrupt();
			try {
				this.join(40000);
			} catch (InterruptedException ix) {
				log.warn("could not verify shutdown");
				return;
			}
			log.info("GemFire Monitor has shut down");
		}
	}

	// jmxHost, jmxPort, userName, password
	public static void main(String[] args) {
		if (args.length < 2) {
			printUsage();
			System.exit(1);
		}

		String jmxHost = null;
		int jmxPort = 0;
		String jmxUser = null;
		String jmxPass = null;

		jmxHost = args[0];
		try {
			jmxPort = Integer.parseInt(args[1]);
		} catch (NumberFormatException x) {
			log.error("second argument should be a port number - provided value: " + args[1]);
			System.exit(1); // EXIT
		}

		if (args.length == 3) {
			log.error("jmx username argument provided but jmx password argument not provided");
			System.exit(1); // EXIT
		}

		if (args.length > 2) {
			jmxUser = args[2];
			jmxPass = args[3];
		}

		if (args.length > 4) {
			log.error("unrecognized extra arguments provided");
			System.exit(1);
		}

		try {
			GemFireMonitor m = new GemFireMonitor(jmxHost, jmxPort, jmxUser, jmxPass);
			Runtime.getRuntime().addShutdownHook(new Thread() {

				@Override
				public void run() {
					m.shutdown();
				}

			});
			m.start();

			System.out.println("monitor started, press Ctrl-C to stop");
		} catch (Exception x) {
			log.error("unexpected exception in monitor, will exit", x);
			System.exit(1); // EXIT
		}
	}

	private static void printUsage() {
		log.info("usage: gemfire-monitor jmx-host jmx-port [jmx-user jmx-password]");
	}
}
