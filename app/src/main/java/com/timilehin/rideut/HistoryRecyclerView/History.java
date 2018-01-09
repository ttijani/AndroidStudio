package com.timilehin.rideut.HistoryRecyclerView;

/**
 * Created by tijanioluwatimilehin on 1/7/18.
 */

public class History
    {
        private String rideID;
        private String time;

        public History()
            {

            }

        public History(String rideId, String time)
            {
                this.rideID = rideId;
                this.time = time;
            }


        public String getRideId()
            {
                return this.rideID;
            }

        public String getTime()
            {
                return this.time;
            }

        public void setTime(String time)
            {
                this.time = time;
            }
    }
