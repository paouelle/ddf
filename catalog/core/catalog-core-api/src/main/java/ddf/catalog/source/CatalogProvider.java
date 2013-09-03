/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package ddf.catalog.source;

import java.util.List;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.Update;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.util.Maskable;

/**
 * External facing (outside of {@link CatalogFramework}) API used to interact
 * with providers of data such as a file system or database. The basic premise
 * of a CatalogProvider is to allow query, create, update, and delete
 * operations.
 * 
 * The provider performs a translation between DDF objects and its native
 * format. The key functions of the CatalogProvider can be found in the
 * {@link Source} and {@link Metacard} interfaces.
 * 
 */
public interface CatalogProvider extends Source, Maskable {

	/**
	 * Publishes a list of {@link Metacard} objects into the catalog.
	 * 
	 * @param createRequest
	 *            - the {@link CreateRequest} that includes a {@link List} of
	 *            {@link Metacard} objects to be stored in a {@link Source}. The
	 *            ID of the {@link Metacard} object will be ignored and
	 *            populated / generated by the {@link CatalogProvider} when the
	 *            record has been stored.
	 * @return the {@link CreateResponse} containing a {@link List} of fully
	 *         populated metacards. This should be similar to the parameter list
	 *         of {@link Metacard} objects but it must have the Metacard ID
	 *         populated.
	 * @throws IngestException
	 *             if any problem occurs when storing the metacards
	 */
	public CreateResponse create(CreateRequest createRequest)
			throws IngestException;

	/**
	 * Updates a list of {@link Metacard} records. {@link Metacard} records that
	 * are not in the Catalog will not be created.
	 * 
	 * @param updateRequest
	 *            - the {@link UpdateRequest} that includes updates to
	 *            {@link Metacard} records that have been previously stored in a
	 *            {@link Source}. A given {@link Attribute} name-value pair in
	 *            this request must uniquely identify zero metacards or one
	 *            metacard in the {@link Source}, otherwise an
	 *            {@link IngestException} will be thrown.
	 * @return the {@link UpdateResponse} containing a {@link List} of
	 *         {@link Update} objects that represent the new (updated) and old
	 *         (previous) {@link Metacard} records.
	 * @throws IngestException
	 *             if an issue occurs during the update such as multiple records
	 *             were matched for a single update entry
	 */
	public UpdateResponse update(UpdateRequest updateRequest)
			throws IngestException;

	/**
	 * Deletes records specified by a list of attribute values such as an id
	 * attribute.
	 * 
	 * @param deleteRequest
	 *            - the {@link DeleteRequest} containing the attribute values
	 *            associated with {@link Metacard}s to delete
	 * @return a {@link DeleteResponse} with {@link Metacard}s that were
	 *         deleted. These {@link Metacard}s are fully populated in
	 *         preparation for any processing services.
	 * @throws IngestException
	 *             if an issue occurs during the delete
	 */
	public DeleteResponse delete(DeleteRequest deleteRequest)
			throws IngestException;

}