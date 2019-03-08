/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

public interface DashBoardActionListener {
    void onItemClicked(Item item);
    void onSelectionChange(int noBefore, int no);
}
