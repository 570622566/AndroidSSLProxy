package me.lty.ssltest.mitm;// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/


/**
 * ConnectionDetails represents the endpoints of a TCP connection,
 * and whether SSL is used.
 */
public class ConnectionDetails {

    private final int m_hashCode;

    private String m_localHost;
    private int m_localPort;
    private String m_remoteHost;
    private int m_remotePort;
    private boolean m_isSecure;

    /**
     * Creates a new ConnectionDetails instance.
     */
    public ConnectionDetails(String localHost, int localPort,
                             String remoteHost, int remotePort,
                             boolean isSecure) {
        m_localHost = localHost.toLowerCase();
        m_localPort = localPort;
        m_remoteHost = remoteHost.toLowerCase();
        m_remotePort = remotePort;
        m_isSecure = isSecure;

        m_hashCode =
                m_localHost.hashCode() ^
                        m_remoteHost.hashCode() ^
                        m_localPort ^
                        m_remotePort ^
                        (m_isSecure ? 0x55555555 : 0);
    }

    public String getDescription() {
        return
                m_localHost + ":" + m_localPort + "->" +
                        m_remoteHost + ":" + m_remotePort;
    }

    public boolean isSecure() {
        return m_isSecure;
    }

    public String getRemoteHost() {
        return m_remoteHost;
    }

    public String getLocalHost() {
        return m_localHost;
    }

    public int getRemotePort() {
        return m_remotePort;
    }

    public int getLocalPort() {
        return m_localPort;
    }

    /**
     * Value based equality.
     *
     * @param other an <code>Object</code> value
     * @return <code>true</code> => <code>other</code> is equal to this object.
     */
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof ConnectionDetails)) {
            return false;
        }

        final ConnectionDetails otherConnectionDetails = (ConnectionDetails) other;

        return
                hashCode() == otherConnectionDetails.hashCode() &&
                        getLocalPort() == otherConnectionDetails.getLocalPort() &&
                        getRemotePort() == otherConnectionDetails.getRemotePort() &&
                        isSecure() == otherConnectionDetails.isSecure() &&
                        getLocalHost().equals(otherConnectionDetails.getLocalHost()) &&
                        getRemoteHost().equals(otherConnectionDetails.getRemoteHost());
    }

    public final int hashCode() {
        return m_hashCode;
    }

}
