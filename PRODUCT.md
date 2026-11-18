# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
- **Comprador:** a person in Ecuador buying from a stranger online (Marketplace, Instagram, OLX). Pays into custody, tracks the shipment, confirms receipt or opens a dispute.
- **Vendedor:** creates the trato, ships with a tracking number, waits for release of the money.
- **Mediador:** reviews evidence from both sides and decides how the retained money is split (total, partial refund, pay the seller, or fast-track for fraud).
- **Finanzas:** watches the custody ledger, the global balance check and deadline reviews.
- **Administrador:** manages users (cédula validated with módulo 10, provincia → ciudad) and the holidays that feed business-day deadlines.

The redesign must work equally for two audiences: the professor and classmates evaluating a live demo (UDLA ISWZ3101, Grupo 10), and real Ecuadorian users the product could launch to under STRATUM S.A.S. Used on both desktop and phone with equal care.

## Product Purpose
TratoSeguro EC is an escrow (pago protegido) platform for person-to-person purchases in Ecuador: the buyer pays, the platform retains the money, and it only releases it to the seller when the buyer confirms receipt. Success means neither side can be scammed: every cent is traceable and every state change has an author and a reason.

## Positioning
A trato is a state machine with business-day deadlines (weekends and Ecuadorian holidays skipped), an auditable history, and a settlement that must always balance: reembolso + comisión (3 %) + neto al vendedor = monto.

## Operating Context
- States: Creada → Pagada → Enviada → Liberada; plus En disputa, Reembolsada, Cancelada.
- Deadlines: límite de envío and límite de revisión, reviewed daily at 6:00 Ecuador time.
- Payments: Stripe Checkout in test mode (card 4242 4242 4242 4242) or simulated payment when no key is configured. Transferencia, DeUna, PayPhone and efectivo are shown as "Próximamente" only.
- In-app notifications, money movements ledger (RETENCION, LIBERACION, COMISION, REEMBOLSO).

## Capabilities and Constraints
- Java 17, Spring Boot 4.1.1 MVC, Thymeleaf server-rendered pages, Bootstrap 5.3.3, Bootstrap Icons, Spring Security (CSRF on every POST), deployed on Render.
- Business logic stays on the server; the redesign changes templates, CSS and light JS only.
- All user-facing text in Spanish (Ecuador), locale es_EC. Money shown as "$ 1.250,00".
- No real money: test mode must always be clearly labeled.

## Brand Commitments
- Name: TratoSeguro EC. Logo: `src/main/resources/static/img/logo.png` and `icono.png`.
- Brand colors are binding: azul oscuro #0a2f5a, azul #135892, turquesa #14b8b4 (turquesa texto #0b6b68).
- The user named modern SaaS dashboards as the target aesthetic (sidebar navigation, KPI cards, step tracker with timestamps, activity timeline, clean tables with status pills, tabs) with tasteful animation.
- Font, copy wording and color scheme (light/dark) were left open.

## Evidence on Hand
- Seed data in `config/DatosIniciales.java`: 5 demo users and 8 tratos covering every state (amounts, guías, disputes, a partial refund 50,00 + 4,50 + 145,50).
- No real customers, testimonials or metrics exist; do not invent them.

## Product Principles
1. Money is always legible: amounts, who holds them, and the next deadline are never more than a glance away.
2. Every action explains its consequence before it happens (what moves, to whom, when).
3. Trust through traceability: show the history, not promises.
4. Test mode is honest and visible, never hidden.

## Accessibility & Inclusion
WCAG AA contrast (already tuned: turquesa only as accent, #0b6b68 for text), visible focus, keyboard reachable actions, respects prefers-reduced-motion.
