# Product

## The problem
- Gartner predicts 90% of B2B buying will be handled by AI agents by 2028.
- Small suppliers aren't ready. Their product data is messy, and they can't connect to
  AI agents on their own.
- AI buying is still early, so AgentShelf must be useful today too: a clean catalog,
  a quote inbox and demand insights.

## Who pays
- Small and mid-size wholesalers and distributors.
- Start with free pilots for 3–5 suppliers, then charge a monthly fee per supplier.

## Features (each one ships in a milestone, see `ROADMAP.md`)
1. **Import**: CSV and Excel upload, a column-mapping screen, and an error report.
2. **AI cleanup**: Claude suggests fixes to names, units, categories, missing attributes and
   short descriptions, and flags duplicates and odd prices. The supplier approves or rejects
   each suggestion.
3. **Rules**: quantity price tiers, minimum order value, maximum discount, stock,
   delivery areas and quote expiry.
4. **Agent gateway**: an MCP server with an API key and a rate limit per buyer agent.
5. **Approval inbox**: the supplier approves, edits or rejects quotes and gets an email for
   each new one.
6. **Insights**: top searches, searches with no results (demand gaps), quotes per agent
   and approval rate.

## MCP tools
| Tool | Input | Returns |
|---|---|---|
| `search_products` | query, category?, maxPrice?, page, size | Products: id, name, unit, from-price, in stock |
| `get_product` | productId | Full details and price tiers |
| `get_price` | productId, quantity | Unit price and total after rules |
| `check_stock` | productId, quantity | Available or not, and available quantity |
| `request_quote` | buyerRef, items (productId, quantity), deliveryArea, notes | quoteId and status `PENDING` |
| `get_quote_status` | quoteId | Status, plus final prices once approved |

- Only `request_quote` writes data.
- Every call needs a buyer agent API key, which decides the supplier.
- Every input is validated, and every call is logged.

## Data model by module (starting point, refine as needed)
Every table also has `id` (UUID), `created_at`, `updated_at` and `version`.

**identity**
- `supplier`: name, slug, currency
- `app_user`: supplier_id, email, password_hash, role

**catalog**
- `product`: supplier_id, sku, name, description, category, unit, base_price, stock_qty, status
- `import_job`: supplier_id, file_name, status, rows_total, rows_failed, error_report

**enrichment**
- `enrichment_job`: supplier_id, batch_id, status, input_tokens, output_tokens
- `product_suggestion`: supplier_id, product_id, field, old_value, new_value, reason,
  confidence, status

**rules**
- `price_tier`: supplier_id, product_id, min_qty, unit_price
- `supplier_rules`: supplier_id, min_order_value, max_discount_pct, delivery_areas,
  quote_expiry_days

**agentgateway**
- `buyer_agent`: supplier_id, name, api_key_hash, rate_limit_per_min, status

**quotes**
- `quote`: supplier_id, buyer_agent_id, buyer_ref, status, total, currency, notes, expires_at
- `quote_item`: quote_id, product_id, quantity, unit_price

**insights**
- `agent_event`: supplier_id, buyer_agent_id, tool, query, result_count

Quote status flow: `PENDING` → `APPROVED`, `REJECTED` or `EXPIRED`.

## AI cleanup design
- One cached system prompt holds the role, the JSON output schema, the allowed categories and
  units, and the rule "never invent specs; mark unknowns".
- Each product's raw fields go in. JSON suggestions come out (field, new value, reason, confidence).
- Create a suggestion only when something actually changes.

## Demo scenario (M6)
1. A fake wholesaler with about 500 hardware products and deliberately messy names.
2. Import the CSV, then approve the AI cleanup.
3. In Claude, ask: "Find 200 PVC pipes for delivery to my area, with the best bulk price."
4. Claude searches, gets the tier price and sends a quote request.
5. The supplier approves it in the dashboard, and Claude confirms it's approved.
6. Record it as a 60-second video for LinkedIn.
