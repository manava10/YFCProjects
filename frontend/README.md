# YFC staff workspace

React + TypeScript frontend for the existing Spring Boot API. Internal staff can sign in, then work through sales, masters, purchasing and stock, fleet and trips, fuel and expenses, and the accounting workflows the backend already implements.

## Run locally

Start the backend with the existing project instructions, then:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Vite forwards `/api`, `/login`, and `/logout` to the backend at http://127.0.0.1:8080. No credentials are embedded. Use an existing internal staff account. Administrators can create branches from the Branches screen.

```sh
npm run build
```

Produces `dist/`. For deployment, configure the web server to serve the SPA with an `index.html` fallback and forward `/api`, `/login`, `/logout` to Spring Boot on the same origin. Vite preview is only a local static preview and does not provide the development API proxy. Preserve secure production session cookies and HTTPS. Serving the frontend directly from Spring Boot requires configuring public static routes; the current backend protects non-public requests.

## Integration rules

- Session cookies remain browser-managed; no tokens or passwords are stored in browser storage.
- Writes use the backend-provided CSRF header/token, refreshed after login and again after a 403.
- Internal roles control visible actions; the backend remains authoritative.
- Commercial totals come from server responses. Prices and quantities are submitted as decimal strings to preserve precision.
- Dropdowns retrieve every page. Tables use the server page size. The overview links to workflows and does not invent KPI totals.
- Customer decisions require contact and evidence references. Recording a quotation as sent does not send messages.
- Accounts can read operations and use finance. They do not get trip, purchase, fleet, or stock write actions. Operations can run sales, supply and fleet, and can submit fuel, expenses, stock requests, post-dispatch crew changes, delivered-weight corrections, and standalone purchase-receipt reversals. A different accounts user approves those reviews. Administrators manage the chart, branches, materials, cash books, diesel rates, and staff accounts.
- GST invoices, payment allocation, valuation and external portals are not in the backend yet, so they are not in this workspace. Subtotals exclude tax.
