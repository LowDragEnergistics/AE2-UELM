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

import java.math.BigInteger;

/**
 * One input shortage: how much was required, how much was available, and the difference.
 */
public record InputRequirement(BigInteger required, BigInteger available, BigInteger missing) {

    public InputRequirement {
        if (required == null || required.signum() <= 0 || available == null || available.signum() < 0 ||
                missing == null || missing.signum() <= 0 || !missing.equals(required.subtract(available))) {
            throw new IllegalArgumentException("A cycle input requirement must satisfy missing = required - available");
        }
    }
}
