/*
 * Copyright (c) 2016 Fraunhofer IGD
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Fraunhofer IGD <http://www.igd.fraunhofer.de/>
 */
package de.fhg.igd.mapviewer.concurrency;

/**
 * Processing strategy interface.
 * 
 * @author Simon Templer
 */
public interface ProcessingStrategy {

	/**
	 * Return if processing is allowed
	 * 
	 * @param size the current number of partial results
	 * @return if processing shall be allowed
	 */
	public boolean allowProcess(int size);

}
