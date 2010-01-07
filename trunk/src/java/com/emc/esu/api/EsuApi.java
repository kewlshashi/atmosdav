// Copyright (c) 2008, EMC Corporation.
// Redistribution and use in source and binary forms, with or without modification, 
// are permitted provided that the following conditions are met:
//
//     + Redistributions of source code must retain the above copyright notice, 
//       this list of conditions and the following disclaimer.
//     + Redistributions in binary form must reproduce the above copyright 
//       notice, this list of conditions and the following disclaimer in the 
//       documentation and/or other materials provided with the distribution.
//     + The name of EMC Corporation may not be used to endorse or promote 
//       products derived from this software without specific prior written 
//       permission.
//
//      THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
//      "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED 
//      TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
//      PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS 
//      BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
//      CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
//      SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
//      INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
//      CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
//      ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
//      POSSIBILITY OF SUCH DAMAGE.
package com.emc.esu.api;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * This interface defines the basic operations available through the ESU web
 * services.
 */
public interface EsuApi {
    /**
     * Creates a new object in the cloud.
     * @param acl Access control list for the new object.  May be null
     * to use a default ACL
     * @param metadata Metadata for the new object.  May be null for
     * no metadata.
     * @param data The initial contents of the object.  May be appended
     * to later.  May be null to create an object with no content.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @return Identifier of the newly created object.
     * @throws EsuException if the request fails.
     */
    public ObjectId createObject( Acl acl, MetadataList metadata, 
            byte[] data, String mimeType );
    
    /**
     * Creates a new object in the cloud.
     * @param acl Access control list for the new object.  May be null
     * to use a default ACL
     * @param metadata Metadata for the new object.  May be null for
     * no metadata.
     * @param data The initial contents of the object.  May be appended
     * to later.  The stream will NOT be closed at the end of the request.
     * @param length The length of the stream in bytes.  If the stream
     * is longer than the length, only length bytes will be written.  If
     * the stream is shorter than the length, an error will occur.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @return Identifier of the newly created object.
     * @throws EsuException if the request fails.
     */
    public ObjectId createObjectFromStream( Acl acl, MetadataList metadata, 
            InputStream data, int length, String mimeType );
    
    /**
     * Creates a new object in the cloud on the specified path.
     * @param path The path to create the object on.
     * @param acl Access control list for the new object.  May be null
     * to use a default ACL
     * @param metadata Metadata for the new object.  May be null for
     * no metadata.
     * @param data The initial contents of the object.  May be appended
     * to later.  May be null to create an object with no content.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @return the ObjectId of the newly-created object for references by ID.
     * @throws EsuException if the request fails.
     */
    public ObjectId createObjectOnPath( ObjectPath path, Acl acl, 
    		MetadataList metadata, 
            byte[] data, String mimeType );
    
    /**
     * Creates a new object in the cloud using a BufferSegment.
     * @param acl Access control list for the new object.  May be null
     * to use a default ACL
     * @param metadata Metadata for the new object.  May be null for
     * no metadata.
     * @param data The initial contents of the object.  May be appended
     * to later.  May be null to create an object with no content.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @return Identifier of the newly created object.
     * @throws EsuException if the request fails.
     */
    public ObjectId createObjectFromSegment( Acl acl, MetadataList metadata, 
            BufferSegment data, String mimeType );

    /**
     * Creates a new object in the cloud using a BufferSegment on the
     * given path.
     * @param path the path to create the object on.
     * @param acl Access control list for the new object.  May be null
     * to use a default ACL
     * @param metadata Metadata for the new object.  May be null for
     * no metadata.
     * @param data The initial contents of the object.  May be appended
     * to later.  May be null to create an object with no content.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @return the ObjectId of the newly-created object for references by ID.
     * @throws EsuException if the request fails.
     */
    public ObjectId createObjectFromSegmentOnPath( ObjectPath path,
    		Acl acl, MetadataList metadata, 
            BufferSegment data, String mimeType );
    
            
    /**
     * Updates an object in the cloud.
     * @param id The ID of the object to update
     * @param acl Access control list for the new object. Optional, default
     * is NULL to leave the ACL unchanged.
     * @param metadata Metadata list for the new object.  Optional,
     * default is NULL for no changes to the metadata.
     * @param data The new contents of the object.  May be appended
     * to later. Optional, default is NULL (no content changes).
     * @param extent portion of the object to update.  May be null to indicate
     * the whole object is to be replaced.  If not null, the extent size must
     * match the data size.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @throws EsuException if the request fails.
     */
    public void updateObject( Identifier id, Acl acl, MetadataList metadata, 
            Extent extent, byte[] data, String mimeType );

    /**
     * Updates an object in the cloud.
     * @param id The ID of the object to update
     * @param acl Access control list for the new object. Optional, default
     * is NULL to leave the ACL unchanged.
     * @param metadata Metadata list for the new object.  Optional,
     * default is NULL for no changes to the metadata.
     * @param data The new contents of the object.  May be appended
     * to later. Optional, default is NULL (no content changes).
     * @param extent portion of the object to update.  May be null to indicate
     * the whole object is to be replaced.  If not null, the extent size must
     * match the data size.
     * @param length The length of the stream in bytes.  If the stream
     * is longer than the length, only length bytes will be written.  If
     * the stream is shorter than the length, an error will occur.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @throws EsuException if the request fails.
     */
    public void updateObjectFromStream( Identifier id, Acl acl, MetadataList metadata, 
            Extent extent, InputStream data, int length, String mimeType );
    
    /**
     * Updates an object in the cloud using a portion of a buffer.
     * @param id The ID of the object to update
     * @param acl Access control list for the new object. Optional, default
     * is NULL to leave the ACL unchanged.
     * @param metadata Metadata list for the new object.  Optional,
     * default is NULL for no changes to the metadata.
     * @param data The new contents of the object.  May be appended
     * to later. Optional, default is NULL (no content changes).
     * @param extent portion of the object to update.  May be null to indicate
     * the whole object is to be replaced.  If not null, the extent size must
     * match the data size.
     * @param mimeType the MIME type of the content.  Optional, 
     * may be null.  If data is non-null and mimeType is null, the MIME
     * type will default to application/octet-stream.
     * @throws EsuException if the request fails.
     */
    public void updateObjectFromSegment( Identifier id, Acl acl, MetadataList metadata, 
            Extent extent, BufferSegment data, String mimeType );
    
    /**
     * Writes the metadata into the object. If the tag does not exist, it is 
     * created and set to the corresponding value. If the tag exists, the 
     * existing value is replaced.
     * @param id the identifier of the object to update
     * @param metadata metadata to write to the object.
     */
    public void setUserMetadata( Identifier id, MetadataList metadata );
    
    /**
     * Sets (overwrites) the ACL on the object.
     * @param id the identifier of the object to change the ACL on.
     * @param acl the new ACL for the object.
     */
    public void setAcl( Identifier id, Acl acl );
            
    /**
     * Deletes an object from the cloud.
     * @param id the identifier of the object to delete.
     */
    public void deleteObject( Identifier id );
    
    /**
     * Fetches the user metadata for the object.
     * @param id the identifier of the object whose user metadata
     * to fetch.
     * @param tags A list of user metadata tags to fetch.  Optional.  If null,
     * all user metadata will be fetched.
     * @return The list of user metadata for the object.
     */
    public MetadataList getUserMetadata( Identifier id, MetadataTags tags );
    
    /**
     * Fetches the system metadata for the object.
     * @param id the identifier of the object whose system metadata
     * to fetch.
     * @param tags A list of system metadata tags to fetch.  Optional.
     * Default value is null to fetch all system metadata.
     * @return The list of system metadata for the object.
     */
    public MetadataList getSystemMetadata( Identifier id, MetadataTags tags );
    
    /**
     * Reads an object's content.
     * @param id the identifier of the object whose content to read.
     * @param extent the portion of the object data to read.  Optional.
     * Default is null to read the entire object.
     * @param buffer the buffer to use to read the extent.  Must be large
     * enough to read the response or an error will be thrown.  If null,
     * a buffer will be allocated to hold the response data.  If you pass
     * a buffer that is larger than the extent, only extent.getSize() bytes
     * will be valid.
     * @return the object data read as a byte array.
     */
    public byte[] readObject( Identifier id, Extent extent, byte[] buffer );
    
    /**
     * Reads an object's content and returns an InputStream to read the content.
     * Since the input stream is linked to the HTTP connection, it is imperative
     * that you close the input stream as soon as you are done with the stream
     * to release the underlying connection.
     * @param id the identifier of the object whose content to read.
     * @param extent the portion of the object data to read.  Optional.
     * Default is null to read the entire object.
     * @return an InputStream to read the object data.
     */
    public InputStream readObjectStream( Identifier id, Extent extent );
    
    /**
     * Returns an object's ACL
     * @param id the identifier of the object whose ACL to read
     * @return the object's ACL
     */
    public Acl getAcl( Identifier id );
    
    /**
     * Deletes metadata items from an object.
     * @param id the identifier of the object whose metadata to 
     * delete.
     * @param tags the list of metadata tags to delete.
     */
    public void deleteUserMetadata( Identifier id, MetadataTags tags );
    
    /**
     * Lists the versions of an object.
     * @param id the object whose versions to list.
     * @return The list of versions of the object.  If the object does
     * not have any versions, the array will be empty.
     */
    public List<Identifier> listVersions( Identifier id );
    
    /**
     * Creates a new immutable version of an object.
     * @param id the object to version
     * @return the id of the newly created version
     */
    public ObjectId versionObject( Identifier id );
    
    /**
     * Lists all objects with the given tag.
     * @param tag the tag to search for
     * @return The list of objects with the given tag.  If no objects
     * are found the array will be empty.
     * @throws EsuException if no objects are found (code 1003)
     */
    public List<Identifier> listObjects( MetadataTag tag );
    
    /**
     * Lists all objects with the given tag.
     * @param tag the tag to search for
     * @return The list of objects with the given tag.  If no objects
     * are found the array will be empty.
     * @throws EsuException if no objects are found (code 1003)
     */
    public List<Identifier> listObjects( String tag );
    
    /**
     * Lists all objects with the given tag and returns both their
     * IDs and their metadata.
     * @param tag the tag to search for
     * @return The list of objects with the given tag.  If no objects
     * are found the array will be empty.
     * @throws EsuException if no objects are found (code 1003)
     */
    public List<ObjectResult> listObjectsWithMetadata( MetadataTag tag );
    
    /**
     * Lists all objects with the given tag and returns both their
     * IDs and their metadata.
     * @param tag the tag to search for
     * @return The list of objects with the given tag.  If no objects
     * are found the array will be empty.
     * @throws EsuException if no objects are found (code 1003)
     */
    public List<ObjectResult> listObjectsWithMetadata( String tag );
    
    /**
     * Returns a list of the tags that are listable the current user's tennant.
     * @param tag optional.  If specified, the list will be limited to the tags
     * under the specified tag.  If null, only top level tags will be returned.
     * @return the list of listable tags.
     */
    public MetadataTags getListableTags( MetadataTag tag );
    
    /**
     * Returns a list of the tags that are listable the current user's tennant.
     * @param tag optional.  If specified, the list will be limited to the tags
     * under the specified tag.  If null, only top level tags will be returned.
     * @return the list of listable tags.
     */
    public MetadataTags getListableTags( String tag );
    
    
    /**
     * Returns the list of user metadata tags assigned to the object.
     * @param id the object whose metadata tags to list
     * @return the list of user metadata tags assigned to the object
     */
    public MetadataTags listUserMetadataTags( Identifier id );
    
    /**
     * Executes a query for objects matching the specified XQuery string.
     * @param xquery the XQuery string to execute against the cloud.
     * @return the list of objects matching the query.  If no objects
     * are found, the array will be empty.
     */
    public List<Identifier> queryObjects( String xquery );

    /**
     * Lists the contents of a directory.
     * @param path the path to list.  Must be a directory.
     * @return the directory entries in the directory.
     */
    public List<DirectoryEntry> listDirectory( ObjectPath path );
    
    /**
     * Returns all of an object's metadata and its ACL in
     * one call.
     * @param id the object's identifier.
     * @return the object's metadata
     */
    public ObjectMetadata getAllMetadata( Identifier id );
    
    /**
     * An Atmos user (UID) can construct a pre-authenticated URL to an 
     * object, which may then be used by anyone to retrieve the 
     * object (e.g., through a browser). This allows an Atmos user 
     * to let a non-Atmos user download a specific object. The 
     * entire object/file is read.
     * @param id the object to generate the URL for
     * @param expiration the expiration date of the URL
     * @return a URL that can be used to share the object's content
     */
    public URL getShareableUrl( Identifier id, Date expiration );
}
