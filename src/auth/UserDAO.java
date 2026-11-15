/*
 * ============================================================================
 * FEATURE: Fast / Slow Moving Classification & Inventory Reorder Logic
 * ============================================================================
 * PURPOSE:
 * Automatically categorize medicines based on weekly sales volume and trigger 
 * low-stock reorder alerts when inventory drops below 50% of normal capacity.
 * 
 * ----------------------------------------------------------------------------
 * BUSINESS RULES & THRESHOLDS:
 * ----------------------------------------------------------------------------
 * 1. FAST MOVING MEDICINES:
 *    - Condition: Weekly sales strictly greater than 300 units (> 300 units/week).
 *    - Normal Stock Capacity: 5,000 units.
 *    - Reorder Point (50% Rule): Trigger a restock alert when current stock 
 *      drops below 2,500 units (< 2,500).
 * 
 * 2. SLOW MOVING MEDICINES:
 *    - Condition: Weekly sales equal to or less than 300 units (<= 300 units/week).
 *    - Normal Stock Capacity: 200 units.
 *    - Reorder Point (50% Rule): Trigger a restock alert when current stock 
 *      drops below 100 units (< 100).
 * 
 * ----------------------------------------------------------------------------
 * WHAT THIS LOGIC MUST DO:
 * ----------------------------------------------------------------------------
 * 1. CALCULATE WEEKLY SALES:
 *    - Query the sales/invoice history for each medicine over the past 7 days 
 *      to get the total units (tablets/items) sold.
 * 
 * 2. ASSIGN MOVEMENT CATEGORY:
 *    - If 7-day sales > 300 -> Mark/treat the item as "FAST_MOVING".
 *    - Otherwise (<= 300)  -> Mark/treat the item as "SLOW_MOVING".
 * 
 * 3. CHECK REORDER STATUS & SUGGEST RESTOCK QUANTITY:
 *    - Compare the medicine's current stock against its category's 50% threshold:
 *      * For FAST_MOVING: If current stock < 2,500 -> Flag as "LOW STOCK".
 *        (Suggested reorder amount = 5,000 - current stock).
 *      * For SLOW_MOVING: If current stock < 100 -> Flag as "LOW STOCK".
 *        (Suggested reorder amount = 200 - current stock).
 * 
 * 4. TRIGGER ALERTS / UI UPDATES:
 *    - Whenever stock is updated (e.g., after a sale) or during inventory checks, 
 *      run this check and send a low-stock alert if the reorder threshold is crossed.
 * ============================================================================
 */
