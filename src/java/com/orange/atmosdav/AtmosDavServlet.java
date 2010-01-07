/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.orange.atmosdav;

import com.emc.esu.api.DirectoryEntry;
import com.emc.esu.api.DownloadHelper;
import com.emc.esu.api.EsuException;
import com.emc.esu.api.MetadataList;
import com.emc.esu.api.ObjectMetadata;
import com.emc.esu.api.ObjectPath;
import com.emc.esu.api.UploadHelper;
import com.emc.esu.api.rest.EsuRestApi;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 *
 * @author yshi7355
 */
public class AtmosDavServlet extends HttpServlet {

    final static Logger _log = Logger.getLogger(AtmosDavServlet.class.getName());


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
    private static final String WEBDAV_INTERNAL_PREFIX = "/webdav";

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

    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {
        super.init();
    }

    /**
     * Return JAXP document builder instance.
     */
    protected DocumentBuilder getDocumentBuilder() throws ServletException {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = null;
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setExpandEntityReferences(false);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new WebdavResolver());
        } catch(ParserConfigurationException e) {
            throw new ServletException("webdavservlet.jaxpfailed");
        }
        return documentBuilder;
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
    }



    /**
     * PROPFIND Method.
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

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
            EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");
            MetadataList metadata = getObjectMetadata(getAtmosPath(href));
            AtmosType obj_type = getObjectType(metadata);
            if ((obj_type == AtmosType.DIRECTORY) && (!href.endsWith("/")))
                href += "/";


            resp.setStatus(SC_MULTI_STATUS);
            resp.setContentType("text/xml; charset=UTF-8");


            // Create multistatus object
            Element root = new Element("multistatus", DAV_NAMESPACE);
            Document xml = new Document(root);

            parseProperties(req, metadata, root, href, type, properties);
            if ((depth > 0) && (obj_type == AtmosType.DIRECTORY)) {
                List<DirectoryEntry> dir_entries = api.listDirectory(getAtmosPath(href));
                for(DirectoryEntry dir_entry:dir_entries) {
                    try {
                        MetadataList entry_metadata = getObjectMetadata(dir_entry.getPath());
//                        String local_name = entry_metadata.getMetadata("objname").getValue();
                        parseProperties(req, entry_metadata, root, dir_entry.getPath().toString(), type, properties);
                    } catch (EsuException e) {
                        if ((e.getCode() != 403) && (e.getCode() != 1003))
                            throw e;
                    }
                }
            }

            String sxml = xml.toXML();
            resp.getWriter().write(sxml);

        } catch (EsuException e) {
            resp.sendError(e.getCode(), e.getMessage());
        } catch (Exception e) {
            resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Exception: "+e.getMessage());
        }

        boolean exists = true;
        
        if (!exists) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, href);
        }
    }

    /**
     * Process a HEAD request for the specified resource.
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
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String href = getPathFromReq(req);

        try {
            EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");
            ObjectPath obj_path = getAtmosPath(href);
            MetadataList metadata = getObjectMetadata(getAtmosPath(href));
            AtmosType obj_type = getObjectType(metadata);

            if (obj_type == AtmosType.REGULAR) {
                resp.setStatus(resp.SC_OK);
                //response.setContentType("application/octet-stream");
                resp.setContentType("text/plain");
                
                String last_modified_str = metadata.getMetadata("mtime").getValue();
                resp.addDateHeader("Last-Modified", ATMOS_DATE_FORMAT.parse(last_modified_str).getTime());

                DownloadHelper down_helper = new DownloadHelper(api, null);
                down_helper.readObject(obj_path, resp.getOutputStream(), false);
            } else if (obj_type == AtmosType.DIRECTORY) {
                resp.sendError(resp.SC_FORBIDDEN, "Directory listing not allowed.");
            } else if (obj_type == AtmosType.NON_EXISTENT) {
                resp.sendError(resp.SC_NOT_FOUND);
            } else {
                resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Internal error: Invalid object type '"+obj_type+"', should be directory or regular");
            }

        } catch (EsuException e) {
            if (e.getCode() == 1003) {
                resp.sendError(resp.SC_NOT_FOUND);
            } else {
                resp.sendError(e.getCode(), e.getMessage());
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
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String href = getPathFromReq(req);

        try {
            EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");
            ObjectPath obj_path = getAtmosPath(href);
            UploadHelper up_helper = new UploadHelper(api);
            up_helper.setMinReadSize(UploadHelper.DEFAULT_BUFFSIZE);

            // first test if object exists
            boolean exists = true;
            boolean partial = false;
            try {
                ObjectMetadata metadata = api.getAllMetadata(obj_path);
            } catch (EsuException e) {
                if (e.getCode() == 404)
                    exists = false;
                else
                    throw e;
            }

            try {
                if (!partial) {
                    if (!exists)
                        up_helper.createObjectOnPath(obj_path, req.getInputStream(), null, null, false);
                    else
                        up_helper.updateObject(obj_path, req.getInputStream(), null, null, false);
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
                    return;
//                    if (!exists)
//                        api.createObjectOnPath(obj_path, null, null, null, null);
//                    Extent extent = new Extent(range.start, range.length);
//                    api.updateObject(obj_path, null, null, extent, content, null);
                }
            } catch (EsuException e) {
                resp.sendError(e.getCode());
                return;
            } finally {
                try {
                    //contentFile.delete();
                } catch (Exception e) {
                    log("DefaultServlet.doPut: couldn't delete temporary file: " + e.getMessage());
                }
            }
            if (!exists)
                resp.setStatus(HttpServletResponse.SC_CREATED);
            else
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (EsuException e) {
            resp.sendError(e.getCode(), e.getMessage());
        } catch (Exception e) {
            resp.sendError(resp.SC_INTERNAL_SERVER_ERROR, "Exception: "+e.getMessage());
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
                                 List<String> properties) throws ParseException {


        Element resp_elt = appendNewElement(root, "response", null);
        AtmosType obj_type = getObjectType(metadata);

        String display_name = URLDecoder.decode(URLDecoder.decode(metadata.getMetadata("objname").getValue()));
        //String display_name = URLDecoder.decode(metadata.getMetadata("objname").getValue());
        if (href.equals("/"))
            display_name = "";
        /*} else if ("directory".equals(obj_type)) {
            href += "/";
        }*/
        href = URLDecoder.decode(href);


        // Generating href element
        appendNewElement(resp_elt, "href", atmosToURL(href));

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

        try {
            EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");
            ObjectPath obj_path = getAtmosPath(href);

            // first test if object exists
            api.deleteObject(obj_path);
        } catch (EsuException e) {
            resp.sendError(e.getCode());
        }
    }


    /**
     * MKCOL Method.
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (req.getContentLength() > 0) {
            resp.sendError(resp.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String path = getPathFromReq(req);
        if (!path.endsWith("/"))
            path += "/";

        MetadataList metadata = getObjectMetadata(getAtmosPath(path));

        if (metadata != null) {
            // it already exists
            //resp.addHeader("Allow", methodsAllowed.toString()); ****
            resp.sendError(SC_METHOD_NOT_ALLOWED);
        }
        Object object = null;


        try {
            EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");

            // does not exist so we create it
            api.createObjectOnPath(getAtmosPath(path), null, null, null, null);
            resp.setStatus(resp.SC_CREATED);
        } catch (EsuException e) {
            resp.sendError(e.getCode(), e.getMessage());
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
        resp.addHeader("DAV", "1");
        resp.addHeader("Allow", determineMethodsAllowed(getPathFromReq(req)));
        resp.addHeader("MS-Author-Via", "DAV");
    }

    /**
     * Determines the methods normally allowed for the resource.
     */
    private String determineMethodsAllowed(String path) {
        AtmosType obj_type = getObjectType(getObjectMetadata(getAtmosPath(path)));

        if (obj_type == AtmosType.NON_EXISTENT) {
            return "OPTIONS, MKCOL, PUT";
        } else if (obj_type == AtmosType.DIRECTORY) {
            return "OPTIONS, GET, HEAD, POST, DELETE, PROPFIND";
        } else {    // REGULAR
            return "OPTIONS, GET, HEAD, POST, DELETE, PROPFIND, PUT";
        }
    }

    /**
     * Parse the content-range header.
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

    private MetadataList getObjectMetadata(ObjectPath obj_path) throws EsuException {
        EsuRestApi api = new EsuRestApi("10.98.176.136", 80, "69a36dbcbe9c4b0cad8ac8d696deed71/Int001", "Vv67+N+2u7SAZsboJwX8+yd2GXc=");

        try {
            return api.getAllMetadata(obj_path).getMetadata();
            //return api.getSystemMetadata(obj_path, null);  // patsh SH due to bug
        } catch (EsuException e) {
            if (e.getCode() == 1008) {
                return api.getAllMetadata(obj_path).getMetadata();
            }
            if (e.getCode() == 404) {
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

    private static final String getPathFromReq(HttpServletRequest req) {
        return req.getRequestURI();
    }

    private static final Element appendNewElement(Element parent, String name, String content) {
        Element elt;
        elt = new Element(name, DAV_NAMESPACE);
        if (content != null)
            elt.appendChild(content);
        parent.appendChild(elt);
        return elt;
    }

    private ObjectPath getAtmosPath(String raw_path) {
        String atmos_path = raw_path;
//        try {
            //atmos_path = URLEncoder.encode(raw_path, "UTF-8");
            atmos_path = atmos_path.replace("%2F", "/");
            atmos_path = atmos_path.replace("+", "%20");
            atmos_path = WEBDAV_INTERNAL_PREFIX + atmos_path;
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
        return new ObjectPath(atmos_path);
    }
    
    private String atmosToURL(String obj_path) {
        String url;
        try {
            url = URLDecoder.decode(obj_path, "UTF-8");
            if (url.startsWith(WEBDAV_INTERNAL_PREFIX)) {
                url = url.substring(WEBDAV_INTERNAL_PREFIX.length());
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return url;
    }

    // --------------------------------------------- WebdavResolver Inner Class
    /**
     * Work around for XML parsers that don't fully respect
     * {@link DocumentBuilderFactory#setExpandEntityReferences(false)}. External
     * references are filtered out for security reasons. See CVE-2007-5461.
     */
    private class WebdavResolver implements EntityResolver {
        public InputSource resolveEntity (String publicId, String systemId) {
            //context.log(sm.getString("webdavservlet.externalEntityIgnored", publicId, systemId));
            return new InputSource(new StringReader("Ignored external entity"));
        }
    }

    // ------------------------------------------------------ Range Inner Class
    private class Range {
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

        public void recycle() {
            start = 0;
            end = 0;
            length = 0;
        }
    }
}
