/*******************************************************************************
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc, Eolos IT Corp and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 *******************************************************************************/

package org.restcomm.sbc.media;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.mobicents.media.server.impl.rtp.crypto.RawPacket;
import org.mobicents.media.server.io.sdp.SessionDescription;
import org.restcomm.sbc.ConfigurationCache;
import org.restcomm.sbc.media.srtp.RtpConnection;


/**
 * @author  ocarriles@eolos.la (Oscar Andres Carriles)
 * @date    28 nov. 2016 22:12:36
 * @class   MediaZone.java
 *
 */
public class MediaZone  {
	
	protected static final int BUFFER=1500;
	private static transient Logger LOG = Logger.getLogger(MediaZone.class);
	
	protected int originalRtpPort;
	protected int originalRtcpPort;
	protected boolean canMux;
	protected int rtpCountLog=ConfigurationCache.getRtpCountLog();
	protected String originalHost;
	protected String proxyHost;
	
	protected String mediaType;
	protected int logCounter=0;
	
	protected boolean running;
	protected boolean suspended;
	
	protected MediaZone mediaZonePeer;
	protected ExecutorService executorService;
	
	protected DatagramChannel channel;
	protected DatagramSocket socket;
	
	protected int packetsSentCounter=0;
	protected int packetsRecvCounter=0;
	
	protected int lastPacketsSentCounter=0;
	protected int lastPacketsRecvCounter=0;
	
	protected int proxyPort;
	protected Direction direction;
	
	protected InetAddress proxyAddress;
	private InetAddress originalAddress;
	protected MediaController controller;
	protected RtpConnection rtpConnection;
	
	public MediaZone(MediaController controller, Direction direction, String mediaType, String originalHost, int originalRtpPort, int originalRtcpPort, boolean canMux, int proxyPort) throws UnknownHostException {
		this.controller=controller;
		this.originalHost=originalHost;
		this.originalRtpPort=originalRtpPort;
		this.originalRtcpPort=originalRtcpPort;
		this.canMux=canMux;
		this.mediaType=mediaType;
		this.direction=direction;
		this.proxyPort=proxyPort;
		originalAddress=InetAddress.getByName(originalHost);
		rtpConnection= new RtpConnection(controller, originalHost, originalRtpPort);
		if(LOG.isTraceEnabled()) {
			LOG.trace("direction "+direction);
		}
			
	}
	
	public void setLocalProxy(String proxyHost) throws UnknownHostException, SocketException {
		this.proxyHost=proxyHost;
		
		InetSocketAddress address = new InetSocketAddress(proxyHost, proxyPort);
		
		try {
			channel=DatagramChannel.open();
			channel.bind(address);
		} catch (IOException e) {
			throw new SocketException(e.getMessage());
		}

		socket = channel.socket();
		
		if(LOG.isTraceEnabled()) {
			LOG.trace("Opened socket "+address.toString()+" for "+this.toPrint());
		}
		
		//socket.setSoTimeout(60000);	
		proxyAddress=address.getAddress();
		
		
		
		
	}
	
	public SessionDescription getLocalSdp() {
		return controller.getSdp();
	}
	
	protected synchronized boolean checkReady() {
		if(!isAttached())
			return false;
		if(originalHost==null || originalRtpPort==0)
			return false;
		if(proxyHost==null || proxyPort==0)
			return false;
		
		return true;
	}
	
	
	protected void fireProxyTimeoutEvent() {
		controller.getMediaSession().fireMediaTimeoutEvent(this);
	    
	}
	
	protected void fireProxyTerminatedEvent() {
		controller.getMediaSession().fireMediaTerminatedEvent(this);
	}
	
	protected void fireProxyReadyEvent() {
		controller.getMediaSession().fireMediaReadyEvent(this);
	}
	
	protected void fireProxyFailedEvent() {
		controller.getMediaSession().fireMediaFailedEvent(this);
	}
	
	public int getOriginalRtpPort() {
		return originalRtpPort;
	}
	
	public String getProxyHost() {
		return proxyHost;
	}
	
	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}
	
	public String getOriginalHost() {
		return originalHost;
	}	
	
	public int getProxyPort() {
		return proxyPort;
	}
	
	public void start() throws UnknownHostException {
		if(isRunning()) {
			LOG.warn("Media Proxy is just running, silently ignoring");
			return;
		}
		
		if(!checkReady()) {
			LOG.warn("Media Zone could not stablish proper routes, should dismiss? "+this.toPrint());
			
		}
		
		setRunning(true);
		
		executorService = Executors.newCachedThreadPool();
		executorService.execute(new Proxy());
		
		/*
		if(!mediaZonePeer.isRunning())
			mediaZonePeer.start();	
		*/
		if(LOG.isInfoEnabled()) {
			LOG.info("Started "+isRunning()+"->"+this.toPrint());		
		}
		
		
		
	}
	public void suspend() {
		if(LOG.isTraceEnabled()) {
			LOG.trace("Suspending mediaZone "+this.toPrint());
		}
		suspended=true;
		
	}
	
	public void resume() {
		if(LOG.isTraceEnabled()) {
			LOG.trace("Resuming mediaZone "+this.toPrint());
		}
		suspended=false;
	}

	public void finalize()  {
		//ensure not traffic
		setRunning(false);
				
		if(LOG.isTraceEnabled()) {
			LOG.trace("Finalizing mediaZone "+this.toPrint());
		}
		
		
		if(mediaZonePeer!=null) {
			setRunning(false);
			if(mediaZonePeer.socket!=null&&!mediaZonePeer.socket.isClosed()) {
				mediaZonePeer.socket.close();	
				mediaZonePeer.socket=null;
				
			}
			if(mediaZonePeer.executorService!=null) {
				mediaZonePeer.executorService.shutdown();
				mediaZonePeer.executorService=null;
				mediaZonePeer.fireProxyTerminatedEvent(); 
				mediaZonePeer=null;
				
			}	
			
		}
			
		if(socket!=null&&!socket.isClosed()) {
        	socket.close();
        	socket=null;
        	
		}
		
		if(executorService!=null) {
			executorService.shutdown();
			executorService=null;
			fireProxyTerminatedEvent(); 
		}
		
           
    }
	
	
	public String toPrint() {
		String value;
		
		value="(UMZ "+direction+") "+this.hashCode()+" "+mediaType+", Origin "+originalHost+":"+originalRtpPort+", LocalProxy "+proxyHost+":"+proxyPort;
		if(mediaZonePeer!=null)
				value+="[("+mediaZonePeer.direction+") "+mediaZonePeer.hashCode()+" "+mediaZonePeer.mediaType+", Origin "+mediaZonePeer.originalHost+":"+mediaZonePeer.originalRtpPort+", LocalProxy "+mediaZonePeer.proxyHost+":"+mediaZonePeer.proxyPort+"]";
		return value;
	}
	
	public byte[] encodeRTP(byte[] data, int offset, int length) {
		/*
		if(LOG.isTraceEnabled()) {
			LOG.trace("VOID Decoding "+length+" bytes");
		}
		*/
		return ArrayUtils.subarray(data, offset, length);			
	}
	
	public byte[] decodeRTP(byte[] data, int offset, int length) {
		/*
		if(LOG.isTraceEnabled()) {
			LOG.trace("VOID Encoding "+length+" bytes");
		}
		*/
		return ArrayUtils.subarray(data, offset, length);		
	}
	
	public void send(DatagramPacket dgram) throws IOException {
		
		if(dgram==null)
			return;
		
		dgram.setAddress(mediaZonePeer.getOriginalAddress());
		dgram.setPort(mediaZonePeer.getOriginalRtpPort());
		//dgram.setData(mediaZonePeer.encodeRTP(dgram.getData(), 0, dgram.getLength()), 0, dgram.getLength() );	
		
		
		if(dgram.getData().length>8) {
				if(logCounter==rtpCountLog&&!mediaType.equals("video")){
					RawPacket rtp=new RawPacket(dgram.getData(),0,dgram.getLength());
					LOG.trace("--->[PayloadType "+rtp.getPayloadType()+"]("+this.mediaType+", "+this.direction+") LocalProxy "+proxyHost+":"+proxyPort+"/"+dgram.getAddress()+":"+dgram.getPort()+"["+dgram.getLength()+"]");
					logCounter=0;
				}	
		}
		else {
			LOG.warn("--->[PayloadType ?]("+this.mediaType+", "+this.direction+") LocalProxy "+proxyHost+":"+proxyPort+"/"+dgram.getAddress()+":"+dgram.getPort()+"["+dgram.getLength()+"]");
		}
		
		packetsSentCounter++;
					
		socket.send(dgram);	
		
		
	}
	
	byte[] buffer=new byte[BUFFER];
	DatagramPacket dgram=new DatagramPacket(buffer, BUFFER);
	public DatagramPacket receive() throws IOException {
	
		if(mediaZonePeer.socket==null) {
			throw new IOException("NULL Socket on "+this.toPrint());
		}
		mediaZonePeer.socket.receive(dgram);
		
		if(dgram==null||dgram.getLength()<8){
			LOG.warn("RTPPacket too short, not sending ["+(dgram!=null?dgram.getLength():"NULL")+"]");
			dgram=new DatagramPacket(buffer, BUFFER);
			return null;
					
		}
		
		dgram.setData(mediaZonePeer.encodeRTP(dgram.getData(), 0, dgram.getLength()));
			
		logCounter++;
		
		if(logCounter==rtpCountLog&&!mediaType.equals("video")){	
			RawPacket rtp=new RawPacket(dgram.getData(),0,dgram.getLength());
			LOG.trace("<---[PayloadType "+rtp.getPayloadType()+"]("+this.mediaType+", "+this.direction+") LocalProxy "+proxyHost+":"+proxyPort+"/"+dgram.getAddress()+":"+dgram.getPort()+"["+dgram.getLength()+"]");		
		}
		
		packetsRecvCounter++;
			
		return dgram;
		
	}
	
	public final void attach(MediaZone mediaZone) {
		if(!isAttached()) {
			setMediaZonePeer(mediaZone);		
		}
		if(!mediaZone.isAttached()) {
			mediaZone.attach(this);
		}
		
		if(checkReady()) {
			// Ready to start
			this.fireProxyReadyEvent();
		}	
		
	}
	
	public boolean isAttached() {
		return mediaZonePeer!=null;
	}
	
	class Proxy implements Runnable {
		@Override
		public void run() {
			while(isRunning())	{
				if(isSuspended()){
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						continue;
					}
					continue;
				}
				try {
					send(receive());	
				} catch (Exception e) {
					if(!isRunning()||!mediaZonePeer.isRunning())
						return;
					//LOG.error("("+MediaZone.this.toPrint()+") "+e.getMessage());
					continue;
					/*
					try {
						finalize();
					} catch (Throwable e1) {
						LOG.error("Cannot finalize stream!");
					}
					break;
					*/
				}		
			}	
		}	
	}
	
	public boolean isStreaming() {
		if(LOG.isTraceEnabled()) {
			LOG.trace("Packets stats on "+this.toPrint());
			LOG.trace("Packets total/sent "+packetsSentCounter+"/"+lastPacketsSentCounter);
			LOG.trace("Packets total/recv "+packetsRecvCounter+"/"+lastPacketsRecvCounter);
		}
		if(packetsSentCounter>lastPacketsSentCounter &&
		   packetsRecvCounter>lastPacketsRecvCounter &&
		   packetsSentCounter>0 &&
		   packetsRecvCounter>0) {
				lastPacketsSentCounter=packetsSentCounter;
				lastPacketsRecvCounter=packetsRecvCounter;	
				return true; 	
       }
       else {
    	   return false;   
       }
		
	}
	
	
	public String getMediaType() {
		return mediaType;
	}

	public MediaZone getMediaZonePeer() {
		return mediaZonePeer;
	}

	protected void setMediaZonePeer(MediaZone mediaZonePeer) {
		this.mediaZonePeer = mediaZonePeer;
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public boolean isSuspended() {
		return suspended;
	}
	
	protected synchronized void setRunning(boolean running) {
		this.running=running;
	}
	
	public int getPacketsSentCounter() {
		return packetsSentCounter;
	}
	public int getPacketsRecvCounter() {
		return packetsRecvCounter;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}
	
	@Override
	public boolean equals(Object zone) {
		MediaZone otherZone=(MediaZone) zone;
		if (!(zone instanceof MediaZone)) {
			return false;
		}
		
		if (otherZone.getOriginalHost().equals(this.getOriginalHost()) &&
			otherZone.getController().equals(this.getController()) &&
			otherZone.getOriginalRtpPort()==this.getOriginalRtpPort()	&&
			otherZone.getMediaType().equals(this.getMediaType())&&
			otherZone.getDirection().equals(this.getDirection())) {
			return true;
		}
		return false;
		
	}
	
	@Override
	public int hashCode() {
		int prime = 31;
		int result = 1;
		result = prime * result + ((controller == null) ? 0 : controller.hashCode());
		result = prime * result + ((originalHost == null) ? 0 : originalHost.hashCode());
		result = prime * result + ((originalRtpPort == 0) ? 0 : originalRtpPort);
		result = prime * result + ((mediaType == null) ? 0 : mediaType.hashCode());
		result = prime * result + ((direction == null) ? 0 : direction.hashCode());
		return result;

	}
	
	public enum Direction {
		OFFER ("*offer"),
        ANSWER("answer");

        private final String text;

        private Direction(final String text) {
            this.text = text;
        }

        public static Direction getValueOf(final String text) {
        	Direction[] values = values();
            for (final Direction value : values) {
                if (value.toString().equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(text + " is not a valid call direction.");
        }


        @Override
        public String toString() {
            return text;
        }
    }

	public Direction getDirection() {
		return direction;
	}

	public InetAddress getProxyAddress() {
		return proxyAddress;
	}

	public InetAddress getOriginalAddress() {
		return originalAddress;
	}

	public MediaController getController() {
		return controller;
	}

	public boolean canMux() {
		return canMux;
	}

	public DatagramChannel getChannel() {
		return channel;
	}

	public RtpConnection getRtpConnection() {
		return rtpConnection;
	}

	
	
	
	
}
