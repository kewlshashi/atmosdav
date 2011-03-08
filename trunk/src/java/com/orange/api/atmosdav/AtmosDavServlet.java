/*
 *	This file is part of atmosdav, a webdav server on top of EMC
 *      Atmos Cloud Storage.
 *	(c) 2010 Stephan Hadinger
 *
 *	This program is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.orange.api.atmosdav;

import com.emc.esu.api.DirectoryEntry;
import com.emc.esu.api.rest.DownloadHelper;
import com.emc.esu.api.EsuException;
import com.emc.esu.api.MetadataList;
import com.emc.esu.api.ObjectPath;
import com.emc.esu.api.rest.UploadHelper;
import com.emc.esu.api.rest.EsuRestApi;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import org.apache.commons.codec.binary.Base64;

/**
 * The Sevlet class hanlding WebDAV requests and converting them to
 * Atmos REST requests.
 *
 * Needed libraries are:
 *  - Apache Commons Codec
 *  - XOM
 *
 * @author Stephan Hadinger
 * @version 0.50
 */
public class AtmosDavServlet extends HttpServlet {

    /*
     * Name of the Servlet parameters containg IP and port of the Atmos endpoint
     */
    private static String ATMOS_HOST_PARAM = "atmos_host";
    private static String ATMOS_PORT_PARAM = "atmos_port";

    // -------------------------------------------------------------- Constants
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";
    private static final String METHOD_PROPFIND = "PROPFIND";
    private static final String METHOD_PROPPATCH = "PROPPATCH";
    private static final String METHOD_MKCOL = "MKCOL";
    private static final String METHOD_COPY = "COPY";
    private static final String METHOD_MOVE = "MOVE";
    private static final String METHOD_LOCK = "LOCK";
    private static final String METHOD_UNLOCK = "UNLOCK";

    /**
     * Status code (405) indicating the method specified is not
     * allowed for the resource.
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;

    /**
     * WEBDAV_INTERNAL_PREFIX - All webdav files are stored inside this folder on Atmos.
     */
    private static final String WEBDAV_INTERNAL_PREFIX = "/webdav_";

    /**
     * PROPFIND - Specify a property mask.
     */
    private static final int FIND_BY_PROPERTY = 0;

    /**
     * PROPFIND - Display all properties.
     */
    private static final int FIND_ALL_PROP = 1;

    /**
     * PROPFIND - Return property names.
     */
    private static final int FIND_PROPERTY_NAMES = 2;

    /**
     * ATMOS_DATE_FORMAT - Pattern used to decode date formats from Atmos metadata.
     * This date format is forced to GMT Time Zone.
     */
    private static final DateFormat ATMOS_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    static {
        ATMOS_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * LAST_MODIFIED_FORMAT - Pattern used to encode the "Last Midified" HTTP response header.
     * This date format is forced to GMT Time Zone.
     */
    private static final DateFormat LAST_MODIFIED_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    static {
        LAST_MODIFIED_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    // -------------------------------------------- Extended WebDav status code
    /**
     * Status code (207) indicating that the response requires
     * providing status for multiple independent operations.
     */
    public static final int SC_MULTI_STATUS = 207;
    // This one colides with HTTP 1.1
    // "207 Parital Update OK"

    private enum AtmosType { NON_EXISTENT, REGULAR, DIRECTORY };

    private String _atmos_host;
    private int _atmos_port;

    /**
     * Initialize this servlet.
     *
     * Fails if the "atmos_host" and "atmos_port" servlet parameters are not
     * correctly configured in the web.xml file.
     */
    @Override
    public void init() throws ServletException {
        _atmos_host = getInitParameter(ATMOS_HOST_PARAM);
        if (_atmos_host == null)
            throw new ServletException("atmos_host parameter missing");

        String atmos_port_str = getInitParameter(ATMOS_PORT_PARAM);
        if (atmos_port_str == null)
            throw new ServletException("_atmos_port parameter missing");
        try {
            _atmos_port = Integer.valueOf(atmos_port_str);
        } catch (Exception e) {
            throw new ServletException("atmos_port parameter incorrect:"+atmos_port_str, e);
        }
        // Note: super() is not needed for this zero-param init() - see Servlet doc
    }

    /**
     * Handles the special WebDAV methods.
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control","no-cache"); //HTTP 1.1
        resp.setHeader("Pragma","no-cache"); //HTTP 1.0
        resp.setDateHeader ("Expires", 0); //prevents caching at the proxy server

        String method = req.getMethod();

        try {
            if (method.equals(METHOD_PROPFIND)) {
                doPropfind(req, resp);
            } else if (method.equals(METHOD_GET)) {
                doGet(req, resp);
            } else if (method.equals(METHOD_HEAD)) {
                doHead(req, resp);
            } else if (method.equals(METHOD_PUT)) {
                doPut(req, resp);
            } else if (method.equals(METHOD_PROPPATCH)) {
                doProppatch(req, resp);
            } else if (method.equals(METHOD_MKCOL)) {
                doMkcol(req, resp);
            } else if (method.equals(METHOD_COPY)) {
                doCopy(req, resp);
            } else if (method.equals(METHOD_MOVE)) {
                doMove(req, resp);
            } else if (method.equals(METHOD_LOCK)) {
                doLock(req, resp);
            } else if (method.equals(METHOD_UNLOCK)) {
                doUnlock(req, resp);
            } else if (method.equals(METHOD_POST)) {
                doPost(req, resp);
            } else if (method.equals(METHOD_DELETE)) {
                doDelete(req, resp);
            } else if (method.equals(METHOD_OPTIONS)) {
                doOptions(req, resp);
            } else if (method.equals(METHOD_TRACE)) {
                doTrace(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            }
        } catch (EsuException e) {
            if (e.getAtmosCode() == 1033) {
                resp.setHeader(WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE);
                resp.sendError(resp.SC_UNAUTHORIZED);
            } else {
                if (e.getHttpCode() != 0) {
                    StringBuffer err = new StringBuffer();

                    err.append(e.getMessage());
                    err.append("<BR />Atmos err code=");
                    err.append(e.getAtmosCode());
                    err.append("<BR />Http code=");
                    err.append(e.getHttpCode());

                    resp.sendError(e.getHttpCode(), err.toString());
                } else {
                    resp.sendError(resp.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
        // Note super() is not called as we do not want some unexpected behaviour http servlets
    }



    /**
     * PROPFIND Method.
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AtmosApi api = getAPIFromAuthent(req, resp);
        
        // Properties which are to be displayed.
        List<String> properties = null;
        // Propfind depth
        int depth = 1;
        // Propfind type
        int type = FIND_ALL_PROP;

        String href = getPathFromReq(req);

        if ("0".equals(req.getHeader("Depth"))) {
            depth = 0;      // only accepted values are 0 and 1
        }

        Element propNode = null;
        if (req.getContentLength() >0) {
            Builder parser = new Builder();

            try {
                Document doc = parser.build(req.getInputStream());

                // Get the root element of the document
                Element root = doc.getRootElement();
                Elements childList = root.getChildElements();

                for (int i=0; i < childList.size(); i++) {
                    Element currentNode = childList.get(i);
                    if (currentNode.getLocalName().equals("prop")) {
                        type = FIND_BY_PROPERTY;
                        propNode = currentNode;
                    }
                    if (currentNode.getLocalName().equals("propname")) {
                        type = FIND_PROPERTY_NAMES;
                    }
                    if (currentNode.getLocalName().equals("allprop")) {
                        type = FIND_ALL_PROP;
                    }
                }
            } catch(Exception e) {
                // Something went wrong - bad request
                resp.sendError(resp.SC_BAD_REQUEST);
                return;
            }
        }
        
        if (type == FIND_BY_PROPERTY) {
            properties = new Vector<String>();
            Elements childList = propNode.getChildElements();
            for (int i=0; i < childList.size(); i++) {
                properties.add(childList.get(i).getLocalName());
            }

        }


        try {
            //EsuRestApi api = new EsuRestApi(ATMOS_ENDPOINT_HOST, ATMOS_ENDPOINT_PORT, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");
            MetadataList metadata = getObjectMetadata(api.api, getAtmosPath(href, api));
            AtmosType obj_type = getObjectType(metadata);

            if (obj_type == AtmosType.NON_EXISTENT) {
                // check if we need to initialize the directory container for webdav
                if ("/".equals(href)) {
                    api.api.createObjectOnPath(getAtmosPath(href, api), null, null, null, null);
                    obj_type = AtmosType.DIRECTORY;
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, href);
                    return;
                }
            }

            if ((obj_type == AtmosType.DIRECTORY) && (!href.endsWith("/")))
                href += "/";

            resp.setStatus(SC_MULTI_STATUS);
            resp.setContentType("text/xml; charset=UTF-8");


            // Create multistatus object
            Element root = new Element("multistatus", DAV_NAMESPACE);
            Document xml = new Document(root);

            parseProperties(req, metadata, root, getAtmosPath(href, api).toString(), type, api, properties);
            if ((depth > 0) && (obj_type == AtmosType.DIRECTORY)) {
                List<DirectoryEntry> dir_entries = api.api.listDirectory(getAtmosPath(href, api));
                for(DirectoryEntry dir_entry:dir_entries) {
                    try {
                        MetadataList entry_metadata = getObjectMetadata(api.api, dir_entry.getPath());
//                        String local_name = entry_metadata.getMetadata("objname").getValue();
                        parseProperties(req, entry_metadata, root, dir_entry.getPath().toString(), type, api, properties);
                    } catch (EsuException e) {
                        if ((e.getAtmosCode() != 403) && (e.getAtmosCode() != 1003))
                            throw e;
                    }
                }
            }

            String sxml = xml.toXML();
            resp.getWriter().write(sxml);

        } catch (EsuException e) {
            resp.sendError(e.getHttpCode(), e.getMessage());
        } catch (Exception e) {
            resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Exception: "+e.getMessage());
        }

    }

    /**
     * Process a HEAD request for the specified resource.
     *
     * It is currently identical to GET.
     * (could be improved someday... but do clients send HEAD ?)
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        doGet(req, resp);
    }


    /**
     * Process a GET request for the specified resource.
     *
     * Note: GET method currently does not support Content-Range parameter.
     * It will send the complete content.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String href = getPathFromReq(req);
        AtmosApi api = getAPIFromAuthent(req, resp);

        try {
            ObjectPath obj_path = getAtmosPath(href, api);
            MetadataList metadata = getObjectMetadata(api.api, getAtmosPath(href, api));
            AtmosType obj_type = getObjectType(metadata);

            if (obj_type == AtmosType.NON_EXISTENT) {
                // check if we need to initialize the directory container for webdav
                if ("/".equals(href)) {
                    api.api.createObjectOnPath(getAtmosPath(href, api), null, null, null, null);
                    obj_type = AtmosType.DIRECTORY;
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, href);
                    return;
                }
            }

            if (obj_type == AtmosType.REGULAR) {
                resp.setStatus(resp.SC_OK);
                //response.setContentType("application/octet-stream");
                resp.setContentType("text/plain");
                
                String last_modified_str = metadata.getMetadata("mtime").getValue();
                resp.addDateHeader("Last-Modified", ATMOS_DATE_FORMAT.parse(last_modified_str).getTime());

                DownloadHelper down_helper = new DownloadHelper(api.api, null);
                down_helper.readObject(obj_path, resp.getOutputStream(), false);
            } else if (obj_type == AtmosType.DIRECTORY) {
                resp.sendError(resp.SC_FORBIDDEN, "Directory listing not allowed.");
            } else if (obj_type == AtmosType.NON_EXISTENT) {
                resp.sendError(resp.SC_NOT_FOUND);
            } else {
                resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Internal error: Invalid object type '"+obj_type+"', should be directory or regular");
            }

        } catch (EsuException e) {
            if ((e.getAtmosCode() == 1003) || (e.getHttpCode() == 404)) {
                resp.sendError(resp.SC_NOT_FOUND);
            } else {
                throw e;
            }
        } catch (Exception e) {
            resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Exception: "+e.getMessage());
        }

        
    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        doGet(req, resp);
    }

    /**
     * Process a PUT request for the specified resource.
     *
     * Note: PUT method currently does not support Content-Range parameter.
     * It will throw a 501 error if Content-Range is present in the header,
     * as expected by RFC.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        AtmosApi api = getAPIFromAuthent(req, resp);
        ObjectPath obj_path = getAtmosPath(getPathFromReq(req), api);

        UploadHelper up_helper = new UploadHelper(api.api);
        up_helper.setMinReadSize(UploadHelper.DEFAULT_BUFFSIZE);

        // first test if object exists
        AtmosType obj_type = getObjectType(getObjectMetadata(api.api, obj_path));
        boolean partial = false;

        // RFC says we MUST reject request containing Content-Range if we don't support it
        if (req.getHeader("Content-Range") != null) {
            resp.sendError(resp.SC_NOT_IMPLEMENTED);
            return;
        }

        if (!partial) {
            if (obj_type == AtmosType.NON_EXISTENT) {
                up_helper.createObjectOnPath(obj_path, req.getInputStream(), null, null, false);
                resp.setStatus(HttpServletResponse.SC_CREATED);
            } else if (obj_type == AtmosType.REGULAR) {
                up_helper.updateObject(obj_path, req.getInputStream(), null, null, false);
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);    // Cannot PUT on a directory
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
//                    if (!exists)
//                        api.createObjectOnPath(obj_path, null, null, null, null);
//                    Extent extent = new Extent(range.start, range.length);
//                    api.updateObject(obj_path, null, null, extent, content, null);
        }
    }

    /**
     * Generate the namespace declarations.
     */
    private static String DAV_NAMESPACE = "DAV:";
    private static String NAMESPACE_DECLARATION = " xmlns=\"DAV:\"";

    private static String STATUS_OK = new String("HTTP/1.1 200 OK");
    private static String STATUS_NOT_FOUND = new String("HTTP/1.1 404 Not Found");

    /**
     * Propfind helper method.
     *
     * @param req The servlet request
     * @param resources Resources object associated with this context
     * @param generatedXML XML response to the Propfind request
     * @param path Path of the current resource
     * @param type Propfind type
     * @param propertiesVector If the propfind type is find properties by
     * name, then this Vector contains those properties
     */
    private void parseProperties(HttpServletRequest req,
                                 MetadataList metadata,
                                 Element root,
                                 String href, int type,
                                 AtmosApi api,
                                 List<String> properties) throws ParseException {


        Element resp_elt = appendNewElement(root, "response", null);
        AtmosType obj_type = getObjectType(metadata);

        String display_name = URLDecoder.decode(URLDecoder.decode(metadata.getMetadata("objname").getValue()));
        //String display_name = URLDecoder.decode(metadata.getMetadata("objname").getValue());
        href = atmosToURL(href, api);

        if (href.equals("/")) {
            display_name = "";
        }
        /*} else if ("directory".equals(obj_type)) {
            href += "/";
        }*/
        href = URLDecoder.decode(href);


        // Generating href element
        appendNewElement(resp_elt, "href", href);

        Element propstat_elt = appendNewElement(resp_elt, "propstat", null);
        Element prop_elt = appendNewElement(propstat_elt, "prop", null);
        
        switch (type) {

        case FIND_PROPERTY_NAMES :

            appendNewElement(prop_elt, "creationdate", null);
            appendNewElement(prop_elt, "displayname", null);
            if (obj_type == AtmosType.REGULAR) {
                appendNewElement(prop_elt, "getcontentlength", null);
                appendNewElement(prop_elt, "getcontenttype", null);
                appendNewElement(prop_elt, "getlastmodified", null);
            }
            appendNewElement(prop_elt, "resourcetype", null);
            appendNewElement(propstat_elt, "status", STATUS_OK);
            break;

        case FIND_ALL_PROP :

            appendNewElement(prop_elt, "creationdate", metadata.getMetadata("ctime").getValue());
            appendNewElement(prop_elt, "displayname", display_name);
            if (obj_type == AtmosType.REGULAR) {
                appendNewElement(prop_elt, "getlastmodified",
                        LAST_MODIFIED_FORMAT.format(ATMOS_DATE_FORMAT.parse(metadata.getMetadata("mtime").getValue()).getTime()));
                appendNewElement(prop_elt, "getcontentlength", metadata.getMetadata("size").getValue());
                appendNewElement(prop_elt, "getcontenttype", "application/octet-stream");
                appendNewElement(prop_elt, "resourcetype", null);
            } else if (obj_type == AtmosType.DIRECTORY) {
                Element type_elt = appendNewElement(prop_elt, "resourcetype", null);
                appendNewElement(type_elt, "collection", null);
            }
            appendNewElement(propstat_elt, "status", STATUS_OK);
            break;

        case FIND_BY_PROPERTY :

            List<String> propertiesNotFound = new Vector<String>();

            // Parse the list of properties

            for(String property:properties) {
                if (property.equals("creationdate")) {
                    appendNewElement(prop_elt, "creationdate", metadata.getMetadata("ctime").getValue());
                } else if (property.equals("displayname")) {
                    appendNewElement(prop_elt, "displayname", display_name);
                } else if (property.equals("getcontentlength")) {
                    if (obj_type == AtmosType.REGULAR) {
                        appendNewElement(prop_elt, "getcontentlength", metadata.getMetadata("size").getValue());
                    } else {
                        propertiesNotFound.add(property);
                    }
                } else if (property.equals("getcontenttype")) {
                    if (obj_type == AtmosType.REGULAR) {
                        appendNewElement(prop_elt, "getcontenttype", "application/octet-stream");
                    } else {
                        propertiesNotFound.add(property);
                    }
                } else if (property.equals("getlastmodified")) {
                    if (obj_type == AtmosType.REGULAR) {
                        appendNewElement(prop_elt, "getlastmodified",
                                LAST_MODIFIED_FORMAT.format(ATMOS_DATE_FORMAT.parse(metadata.getMetadata("mtime").getValue()).getTime()));
                    } else {
                        propertiesNotFound.add(property);
                    }
                } else if (property.equals("resourcetype")) {
                    if (obj_type == AtmosType.REGULAR) {
                        appendNewElement(prop_elt, "resourcetype", null);
                    } else {
                        Element type_elt = appendNewElement(prop_elt, "resourcetype", null);
                        appendNewElement(type_elt, "collection", null);
                    }
                } else {
                    propertiesNotFound.add(property);
                }
            }

            appendNewElement(propstat_elt, "status", STATUS_OK);

            if (propertiesNotFound.size() > 0) {
                propstat_elt = appendNewElement(resp_elt, "propstat", null);
                prop_elt = appendNewElement(propstat_elt, "prop", null);
                for(String not_found:propertiesNotFound)
                    appendNewElement(prop_elt, not_found, null);
                appendNewElement(propstat_elt, "status", STATUS_NOT_FOUND);
            }
            break;

        }
    }

    /**
     * PROPPATCH Method.
     */
    protected void doProppatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * LOCK Method.
     */
    protected void doLock(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * UNLOCK Method.
     */
    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * COPY Method.
     */
    protected void doCopy(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * MOVE Method.
     */
    protected void doMove(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * DELETE Method.
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String href = getPathFromReq(req);
        AtmosApi api = getAPIFromAuthent(req, resp);

        try {
            ObjectPath obj_path = getAtmosPath(href, api);

            // first test if object exists
            api.api.deleteObject(obj_path);
        } catch (EsuException e) {
            resp.sendError(e.getAtmosCode());
        }
    }


    /**
     * MKCOL Method.
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AtmosApi api = getAPIFromAuthent(req, resp);

        if (req.getContentLength() > 0) {
            resp.sendError(resp.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String path = getPathFromReq(req);
        if (!path.endsWith("/"))
            path += "/";

        MetadataList metadata = getObjectMetadata(api.api, getAtmosPath(path, api));

        if (metadata != null) {
            // it already exists
            //resp.addHeader("Allow", methodsAllowed.toString()); ****
            resp.sendError(SC_METHOD_NOT_ALLOWED);
        }
        Object object = null;


        try {
            // does not exist so we create it
            api.api.createObjectOnPath(getAtmosPath(path, api), null, null, null, null);
            resp.setStatus(resp.SC_CREATED);
        } catch (EsuException e) {
            resp.sendError(e.getHttpCode(), e.getMessage());
        }
    }


    /**
     * OPTIONS Method.
     *
     * @param req The request
     * @param resp The response
     * @throws ServletException If an error occurs
     * @throws IOException If an IO error occurs
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AtmosApi api = getAPIFromAuthent(req, resp);
        resp.addHeader("DAV", "1");
        resp.addHeader("Allow", determineMethodsAllowed(api, getPathFromReq(req)));
        resp.addHeader("MS-Author-Via", "DAV");
    }

    private static final String BASICAUTH_HEADER = "Authorization";
    private static final String BASICAUTH_METHOD = "Basic ";
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String WWW_AUTHENTICATE_VALUE = "BASIC realm=\"Atmos credentials\"";

    private AtmosApi getAPIFromAuthent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Get the Authorization header, if one was supplied
        String authorization = req.getHeader(BASICAUTH_HEADER);

        if ((authorization != null) && (authorization.startsWith(BASICAUTH_METHOD))) {
            String login_passwd = stringDecodeBase64(authorization.substring(BASICAUTH_METHOD.length()));
            int pos = login_passwd.indexOf(":");
            if (pos > 0) {
                String login = login_passwd.substring(0, pos);
                String passwd = login_passwd.substring(pos+1);

                if ((login.length() > 0) && (passwd.length() > 0)) {
                    EsuRestApi api = new EsuRestApi(_atmos_host, _atmos_port, login, passwd);
                    AtmosApi api_container = new AtmosApi();
                    api_container.api = api;
                    api_container.uid = login;
                    return api_container;
                }
            }
        }
        throw new EsuException("Credentials missing", 401, 1033);
    }

    private static final String stringDecodeBase64(String base64) {
        byte[] raw_base64;

        if (base64 == null)
            return null;
        try {
            raw_base64 = base64.getBytes("UTF-8");
        } catch(UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8", e);
        }
        return new String(Base64.decodeBase64(raw_base64));
    }


    /**
     * Determines the methods normally allowed for the resource.
     */
    private String determineMethodsAllowed(AtmosApi api, String path) {
        AtmosType obj_type = getObjectType(getObjectMetadata(api.api, getAtmosPath(path, api)));

        if (obj_type == AtmosType.NON_EXISTENT) {
            return "OPTIONS, MKCOL, PUT";
        } else if (obj_type == AtmosType.DIRECTORY) {
            return "OPTIONS, GET, HEAD, POST, DELETE, PROPFIND";
        } else {    // REGULAR
            return "OPTIONS, GET, HEAD, POST, DELETE, PROPFIND, PUT";
        }
    }

    /**
     * Parse the "Content-Range" header.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Range
     */
    protected Range parseContentRange(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Retrieving the content-range header (if any is specified
        String rangeHeader = request.getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return range;
    }

    /**
     * Retrives Object System metadata from an Atmos path.
     *
     * Returns null if the object does not exists. May return EsuException if
     * a problem occured.
     *
     * @param api the Atmos REST API object
     * @param obj_path the Atmos path of the object
     * @return the MetadataList of the object or null if the object does not exist.
     */
    private MetadataList getObjectMetadata(EsuRestApi api, ObjectPath obj_path) {

        try {
            return api.getAllMetadata(obj_path).getMetadata();
        } catch (EsuException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            else
                throw e;
        }
    }

    private AtmosType getObjectType(MetadataList metadata) {
        String obj_type = (metadata != null) ? metadata.getMetadata("type").getValue() : null;
        if ("directory".equals(obj_type))
            return AtmosType.DIRECTORY;
        else if ("regular".equals(obj_type))
            return AtmosType.REGULAR;
        else
            return AtmosType.NON_EXISTENT;
    }

    /**
     *
     * This is just a wrapper so that we place in a central point the
     * retrieval of the HREF from the http request.
     *
     * @param req
     * @return
     */
    private static final String getPathFromReq(HttpServletRequest req) {
        return req.getRequestURI();
    }

    /**
     * Appends a new element with a TAG-NAME and a CONTENT to a parent,
     * using the DAV namespace. It wraps the XOM API.
     *
     * Syntactic sugar to make source look great.
     *
     * @param parent
     * @param name
     * @param content
     * @return
     */
    private static final Element appendNewElement(Element parent, String name, String content) {
        Element elt;
        elt = new Element(name, DAV_NAMESPACE);
        if (content != null)
            elt.appendChild(content);
        parent.appendChild(elt);
        return elt;
    }

    /**
     * Converts from a raw URI from the browser to the URI used to store the
     * Atmos Object.
     *
     * It basically escapes all non-URL-friendly characters and adds
     * the "/webdav" prefix.
     *
     * @param raw_path the dav URI sent by the dav browser
     * @return the URI to be used by Atmos
     */
    private ObjectPath getAtmosPath(String raw_path, AtmosApi api) {
        String atmos_path = raw_path;
//        try {
//            atmos_path = atmos_path.replace("%20", "+");
        // first escape all unsafe characters, except A-Za-z0-9_-.
        // also leave % untouched because it is already escaped
        atmos_path = AtmosURLEncoder.encode(raw_path);
//            atmos_path = atmos_path.replace("%2F", "/");
            
//            atmos_path = atmos_path.replace("+", "%20");
//            atmos_path = atmos_path.replace("!", "%21");
//            atmos_path = atmos_path.replace("#", "%23");
//            atmos_path = atmos_path.replace("$", "%24");
//            atmos_path = atmos_path.replace("&", "%26");
//            atmos_path = atmos_path.replace("'", "%27");
//            atmos_path = atmos_path.replace("(", "%28");
//            atmos_path = atmos_path.replace(")", "%29");
//            atmos_path = atmos_path.replace("*", "%2a");
//            atmos_path = atmos_path.replace(",", "%2c");
//            atmos_path = atmos_path.replace(":", "%3a");
//            atmos_path = atmos_path.replace(";", "%3b");
//            atmos_path = atmos_path.replace("<", "%3c");
//            atmos_path = atmos_path.replace("=", "%3d");
//            atmos_path = atmos_path.replace(">", "%3e");
//            atmos_path = atmos_path.replace("?", "%3f");
//            atmos_path = atmos_path.replace("@", "%40");

            // finally force the encoding of '%'
            atmos_path = atmos_path.replace("%", "%25");

            atmos_path = WEBDAV_INTERNAL_PREFIX + api.getSubTenantId() + atmos_path;
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
        return new ObjectPath(atmos_path);
    }

    /**
     * Converts a URI returned by Atmos (such as ListObjects) to the URI
     * to be used by the dav browser.
     *
     * Basically it removes the "/webdav" prefix.
     *
     * @param url url returned by some Atmos API
     * @return uri to be sent to the dav client
     */
    private String atmosToURL(String url, AtmosApi api) {
        String prefix = WEBDAV_INTERNAL_PREFIX + api.getSubTenantId();
        if (url.startsWith(prefix)) {
            url = url.substring(prefix.length());
        }
        return url;
    }

    protected class AtmosApi {
        public EsuRestApi api = null;
        public String uid = null;

        public String getSubTenantId() {
            int pos = uid.indexOf("/");
            if (pos > 0) {
                return uid.substring(pos+1);
            } else {
                return "";
            }
        }
    }

    // ------------------------------------------------------ Range Inner Class
    protected class Range {
        public long start;
        public long end;
        public long length;

        /**
         * Validate range.
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return ( (start >= 0) && (end >= 0) && (start <= end) && (length > 0) );
        }
    }

}

class AtmosURLEncoder {


    /**
     * Hidden constructor.
     */
    private AtmosURLEncoder() { }

    public static String encode(String s) {
	int maxBytesPerChar = 10; // rather arbitrary limit, but safe for now
        StringBuffer out = new StringBuffer(s.length());
	ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);

        try {
            OutputStreamWriter writer = new OutputStreamWriter(buf, dfltEncName);

            for (int i = 0; i < s.length(); i++) {
                int c = (int) s.charAt(i);

                if (dontNeedEncoding.get(c)) {
                    out.append((char)c);
                } else {
                    // convert to external encoding before hex conversion
                    try {
                        writer.write(c);
                        /*
                         * If this character represents the start of a Unicode
                         * surrogate pair, then pass in two characters. It's not
                         * clear what should be done if a bytes reserved in the
                         * surrogate pairs range occurs outside of a legal
                         * surrogate pair. For now, just treat it as if it were
                         * any other character.
                         */
                        if (c >= 0xD800 && c <= 0xDBFF) {
                            if ( (i+1) < s.length()) {
                                int d = (int) s.charAt(i+1);
                                if (d >= 0xDC00 && d <= 0xDFFF) {
                                    writer.write(d);
                                    i++;
                                }
                            }
                        }
                        writer.flush();
                    } catch(IOException e) {
                        buf.reset();
                        continue;
                    }
                    byte[] ba = buf.toByteArray();
                    for (int j = 0; j < ba.length; j++) {
                        out.append('%');
                        char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
                        // converting to use uppercase letter as part of
                        // the hex value if ch is a letter.
                        if (Character.isLetter(ch)) {
                            ch -= caseDiff;
                        }
                        out.append(ch);
                        ch = Character.forDigit(ba[j] & 0xF, 16);
                        if (Character.isLetter(ch)) {
                            ch -= caseDiff;
                        }
                        out.append(ch);
                    }
                    buf.reset();
                }
            }
            return out.toString();
        } catch (UnsupportedEncodingException e) {
            return s;
        }

    }


    static final int caseDiff = ('a' - 'A');
    static private BitSet dontNeedEncoding;
    static private String dfltEncName = "UTF-8";

    static 
    {
	dontNeedEncoding = new BitSet(256);
	int i;
	for (i = 'a'; i <= 'z'; i++) {
	    dontNeedEncoding.set(i);
	}
	for (i = 'A'; i <= 'Z'; i++) {
	    dontNeedEncoding.set(i);
	}
	for (i = '0'; i <= '9'; i++) {
	    dontNeedEncoding.set(i);
	}
	dontNeedEncoding.set('-');
	dontNeedEncoding.set('_');
	dontNeedEncoding.set('.');
	dontNeedEncoding.set('/');
	dontNeedEncoding.set('%');  // don't do additional escaping

    }

}