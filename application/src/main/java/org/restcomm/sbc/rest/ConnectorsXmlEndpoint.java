/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
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
 */
package org.restcomm.sbc.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;


import static javax.ws.rs.core.MediaType.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.mobicents.servlet.sip.restcomm.annotations.concurrency.ThreadSafe;
import org.restcomm.sbc.bo.Sid;



/**
 * @author  ocarriles@eolos.la (Oscar Andres Carriles)
 * @date    27 jul. 2016 19:44:29
 * @class   ConnectorsXmlEndpoint.java
 *
 */
@Path("/Connectors")
@ThreadSafe
public final class ConnectorsXmlEndpoint extends ConnectorsEndpoint {
    public ConnectorsXmlEndpoint() {
        super();
    }

    @Path("/{sid}.json")
    @GET
    public Response getConnectorAsJson(@PathParam("sid") final String sid) {
        return getConnector(new Sid(sid), APPLICATION_JSON_TYPE);
    }

    @Path("/{sid}")
    @GET
    public Response getConnectorAsXml(@PathParam("sid") final String sid) {
        return getConnector(new Sid(sid), APPLICATION_XML_TYPE);
    }
    
    @Path("/{sid}")
    @DELETE
    public Response deleteConnectorAsXml(@PathParam("sid") final String sid) {
        return deleteConnector(new Sid(sid));
    }

    @GET
    public Response getConnectors(@Context UriInfo info) {
        return getConnectors(info, APPLICATION_XML_TYPE);
    }

    @Consumes(APPLICATION_FORM_URLENCODED)
    @POST
    public Response putConnector(final MultivaluedMap<String, String> data) {
        return putConnector(data, APPLICATION_XML_TYPE);
    }
    
    @Path("/{sid}")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @POST
    public Response updateAccountAsXmlPost(@PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data) {
    	return updateConnector(new Sid(sid), data, APPLICATION_XML_TYPE);
        
    }
    
    
}
