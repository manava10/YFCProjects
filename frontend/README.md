# YFC staff workspace

React + TypeScript frontend for the existing Spring Boot API. This first release includes session sign-in/sign-out, paginated enquiry and order lists, multi-material enquiry creation, quotation revisions, recording send/accept/reject actions, conversion to orders, order quantity details, customer/project/site creation, and an administrator-managed material catalogue.

## Run locally

Start the backend with the existing project instructions, then:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Vite forwards `/api`, `/login`, and `/logout` to the backend at http://localhost:8080. No credentials are embedded. Use an existing internal staff account. Projects require an existing active branch; branch administration remains in the backend for this release.

```sh
npm run build
```

Produces `dist/`. For deployment, configure the web server to serve the SPA with an `index.html` fallback and forward `/api`, `/login`, `/logout` to Spring Boot on the same origin. Vite preview is only a local static preview and does not provide the development API proxy. Preserve secure production session cookies and HTTPS. Serving the frontend directly from Spring Boot requires configuring public static routes; the current backend protects non-public requests.

## Integration rules

- Session cookies remain browser-managed; no tokens or passwords are stored in browser storage.
- Writes use the backend-provided CSRF header/token, refreshed after login.
- Internal roles control visible actions; the backend remains authoritative.
- Commercial totals come from server responses. Quotation prices and quantities are submitted as decimal strings to preserve precision.
- Dropdowns retrieve all pages; tables use server pagination. Overview contains workflow links, not invented KPI totals.
- Customer decisions require contact and evidence references. Recording a quotation as sent does not send messages.
- GST invoicing, dispatch execution, inventory, finance, external portals and branch permissions are not included in this release.
