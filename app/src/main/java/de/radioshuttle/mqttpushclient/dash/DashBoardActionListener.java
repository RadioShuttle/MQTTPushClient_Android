/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

public interface DashBoardActionListener {
    void onItemClicked(Item item);
    void onSelectionChange(int noBefore, int no);
}
