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

import java.util.List;

/**
 * Outcome of compressed repeat scheduling.
 */
public sealed interface CycleScheduleResult permits CycleScheduleResult.Success, CycleScheduleResult.Failure {

    boolean successful();

    /**
     * @return merged executable batches, or null on failure
     */
    List<LoopFiring> schedule();

    /**
     * @return rejection reason, or null on success
     */
    CycleFailureCode failureCode();

    record Success(List<LoopFiring> schedule) implements CycleScheduleResult {
        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public List<LoopFiring> schedule() {
            return schedule;
        }

        @Override
        public CycleFailureCode failureCode() {
            return null;
        }
    }

    record Failure(CycleFailureCode failureCode) implements CycleScheduleResult {
        @Override
        public boolean successful() {
            return false;
        }

        @Override
        public List<LoopFiring> schedule() {
            return null;
        }

        @Override
        public CycleFailureCode failureCode() {
            return failureCode;
        }
    }
}
