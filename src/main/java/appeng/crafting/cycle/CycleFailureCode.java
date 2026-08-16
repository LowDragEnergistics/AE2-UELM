/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.cycle;

/**
 * Structured rejection reasons for cycle planning, mirroring DataEnergistics' planning diagnostics.
 */
public enum CycleFailureCode {
    /**
     * The cycle does not produce a positive net amount of the requested target.
     */
    NO_PRODUCTIVE_CYCLE,
    /**
     * The inventory (plus producible inputs) cannot cover the required seed/consumed inputs.
     */
    INSUFFICIENT_INPUT,
    /**
     * No rotation of the cycle can be executed from the current balances.
     */
    NO_EXECUTABLE_ORDER,
    /**
     * The compressed scheduling state limit was exceeded.
     */
    SEARCH_LIMIT
}
