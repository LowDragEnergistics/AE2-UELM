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
 * Outcome of one cycle plan request.
 */
public sealed interface CyclePlanResult permits CyclePlanResult.Success, CyclePlanResult.Failure {

    boolean successful();

    /**
     * @return the computed plan, or null on failure
     */
    CyclePlan plan();

    /**
     * @return the rejection reason, or null on success
     */
    CycleFailure failure();

    record Success(CyclePlan plan) implements CyclePlanResult {
        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public CyclePlan plan() {
            return plan;
        }

        @Override
        public CycleFailure failure() {
            return null;
        }
    }

    record Failure(CycleFailure failure) implements CyclePlanResult {
        @Override
        public boolean successful() {
            return false;
        }

        @Override
        public CyclePlan plan() {
            return null;
        }

        @Override
        public CycleFailure failure() {
            return failure;
        }
    }
}
