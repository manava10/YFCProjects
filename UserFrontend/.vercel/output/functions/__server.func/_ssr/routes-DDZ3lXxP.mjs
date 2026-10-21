import { i as __toESM } from "../_runtime.mjs";
import { K as require_react, b as require_jsx_runtime } from "../_libs/@tanstack/react-router+[...].mjs";
import { i as Check, r as Menu, t as X } from "../_libs/lucide-react.mjs";
import { n as create, t as persist } from "../_libs/zustand.mjs";
//#region node_modules/.nitro/vite/services/ssr/assets/routes-DDZ3lXxP.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_runtime = require_jsx_runtime();
var useDesk = create()(persist((set) => ({
	tickets: [],
	activeId: null,
	enquiries: [],
	pickedMaterialId: null,
	pickedPlantId: null,
	addTicket: (ticket) => set((state) => ({
		tickets: [ticket, ...state.tickets].slice(0, 24),
		activeId: ticket.id
	})),
	setStage: (id, stage) => set((state) => ({ tickets: state.tickets.map((ticket) => ticket.id === id ? {
		...ticket,
		stage
	} : ticket) })),
	setActive: (id) => set({ activeId: id }),
	addEnquiry: (enquiry) => set((state) => ({ enquiries: [enquiry, ...state.enquiries].slice(0, 12) })),
	pickMaterial: (id) => set({ pickedMaterialId: id }),
	pickPlant: (id) => set({ pickedPlantId: id })
}), {
	name: "yfc-desk",
	skipHydration: true,
	partialize: (state) => ({
		tickets: state.tickets,
		activeId: state.activeId,
		enquiries: state.enquiries,
		pickedMaterialId: state.pickedMaterialId,
		pickedPlantId: state.pickedPlantId
	})
}));
function nextTicketId() {
	return `YFC-${Date.now().toString().slice(-4)}${Math.floor(Math.random() * 10)}`;
}
var fieldClass$1 = "h-12 w-full rounded-xl border border-fg/15 bg-ink px-3 text-sm text-fg outline-none";
function Contact() {
	const addEnquiry = useDesk((state) => state.addEnquiry);
	const [name, setName] = (0, import_react.useState)("");
	const [phone, setPhone] = (0, import_react.useState)("");
	const [note, setNote] = (0, import_react.useState)("");
	const [saved, setSaved] = (0, import_react.useState)("");
	const [error, setError] = (0, import_react.useState)("");
	function submit(event) {
		event.preventDefault();
		if (name.trim().length < 2 || phone.trim().length < 8) {
			setError("Leave a name and a phone number the yard can use.");
			setSaved("");
			return;
		}
		const id = nextTicketId();
		addEnquiry({
			id,
			name: name.trim(),
			phone: phone.trim(),
			note: note.trim(),
			createdAt: Date.now()
		});
		setError("");
		setSaved(id);
		setName("");
		setPhone("");
		setNote("");
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "contact",
		className: "scroll-mt-20 border-t border-fg/10",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto grid max-w-6xl gap-10 px-5 py-20 md:px-8 md:py-28 lg:grid-cols-2",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "CONTACT US"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight",
					children: "Dispatch desk, Kotputli."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-4 max-w-md text-sm leading-relaxed text-muted",
					children: "YFC Transport · Premium building material supply & logistics · Since 2004. Pan India delivery from the Kotputli crusher and the NCR yards."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-6 font-display text-2xl font-semibold",
					children: "Building India. Delivering Trust."
				})
			] }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
				onSubmit: submit,
				className: "rounded-2xl border border-fg/10 bg-navy p-5",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "block text-sm text-muted",
						children: ["Name", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
							className: `${fieldClass$1} mt-2`,
							value: name,
							onChange: (event) => setName(event.target.value)
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "mt-4 block text-sm text-muted",
						children: ["Phone", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
							className: `${fieldClass$1} mt-2`,
							inputMode: "tel",
							value: phone,
							onChange: (event) => setPhone(event.target.value)
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "mt-4 block text-sm text-muted",
						children: ["Note for the yard", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
							className: "mt-2 min-h-28 w-full rounded-xl border border-fg/15 bg-ink px-3 py-3 text-sm text-fg outline-none",
							value: note,
							onChange: (event) => setNote(event.target.value)
						})]
					}),
					error ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-3 text-sm text-orange",
						role: "alert",
						children: error
					}) : null,
					saved ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
						className: "mt-3 text-sm text-fg",
						role: "status",
						children: [
							"Saved on this desk as ",
							saved,
							". It stays on this device — share it with the yard."
						]
					}) : null,
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "submit",
						className: "mt-5 inline-flex h-12 items-center rounded-full bg-orange px-6 text-sm font-semibold text-ink",
						children: "Send to desk"
					})
				]
			})]
		})
	});
}
function Footer() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("footer", {
		className: "border-t border-fg/10 bg-navy",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto flex max-w-6xl flex-col gap-6 px-5 py-12 md:flex-row md:items-end md:justify-between md:px-8",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
				className: "font-display text-4xl font-bold tracking-tight",
				children: ["YFC ", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "text-orange",
					children: "TRANSPORT"
				})]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-2 text-sm text-muted",
				children: "Building India. Delivering Trust."
			})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-sm text-muted",
				children: "Since 2004 · Kotputli, Rajasthan · Pan India delivery"
			})]
		})
	});
}
var materials = [
	{
		id: "20mm",
		name: "20 MM Aggregate",
		rate: 820,
		grade: "20 mm",
		use: "RCC, foundations, and road base that needs a larger lock.",
		image: "/media/agg-20.jpg"
	},
	{
		id: "10mm",
		name: "10 MM Aggregate",
		rate: 850,
		grade: "10 mm",
		use: "Slabs, precast, and mixes that call for a tighter grade.",
		image: "/media/agg-10.jpg"
	},
	{
		id: "vsi",
		name: "VSI Aggregate",
		rate: 780,
		grade: "Cubical",
		use: "Vertical-shaft impact stone. More cubical, cleaner interlock.",
		image: "/media/agg-vsi.jpg"
	},
	{
		id: "dust",
		name: "Stone Dust",
		rate: 650,
		grade: "Fines",
		use: "Filling, blending, and levelling under slabs and pavers.",
		image: "/media/agg-dust.jpg"
	},
	{
		id: "msand",
		name: "M Sand",
		rate: 950,
		grade: "Manufactured sand",
		use: "Plaster and mortar where a graded manufactured sand is specified.",
		image: "/media/agg-msand.jpg"
	}
];
var plants = [
	{
		id: "kotputli",
		name: "Kotputli Crusher Plant",
		place: "Kotputli, Rajasthan",
		note: "Primary crush and screen. All five grades load from this yard.",
		image: "/media/plant.jpg"
	},
	{
		id: "behror",
		name: "Behror Crusher Plant",
		place: "Behror, Rajasthan",
		note: "Screening and fast loading on the NH48 run toward NCR.",
		image: "/media/hero.jpg"
	},
	{
		id: "bhiwadi",
		name: "Bhiwadi Dispatch Hub",
		place: "Bhiwadi, Rajasthan",
		note: "Staging yard for Gurugram, Manesar, and south Delhi drops.",
		image: "/media/truck.jpg"
	}
];
var destinations = [
	"Gurugram, Haryana",
	"Manesar, Haryana",
	"Faridabad, Haryana",
	"Delhi",
	"Noida, Uttar Pradesh",
	"Bhiwadi, Rajasthan",
	"Neemrana, Rajasthan",
	"Alwar, Rajasthan",
	"Jaipur, Rajasthan"
];
var stats = [
	{
		value: "79,200+",
		label: "Trips Completed"
	},
	{
		value: "22+",
		label: "Years of Excellence"
	},
	{
		value: "500+",
		label: "Happy Clients"
	},
	{
		value: "100+",
		label: "Fleet Partners"
	},
	{
		value: "Pan India",
		label: "Delivery"
	}
];
var nav = [
	{
		href: "#top",
		label: "Home"
	},
	{
		href: "#about",
		label: "About Us"
	},
	{
		href: "#materials",
		label: "Materials"
	},
	{
		href: "#plants",
		label: "Our Plants"
	},
	{
		href: "#rates",
		label: "Live Rates"
	},
	{
		href: "#track",
		label: "Track Order"
	},
	{
		href: "#contact",
		label: "Contact Us"
	}
];
var stages = [
	"Confirmed",
	"Loading",
	"On the Way",
	"Delivered"
];
var freight = {
	kotputli: {
		"Gurugram, Haryana": 420,
		"Manesar, Haryana": 390,
		"Faridabad, Haryana": 400,
		Delhi: 460,
		"Noida, Uttar Pradesh": 490,
		"Bhiwadi, Rajasthan": 240,
		"Neemrana, Rajasthan": 180,
		"Alwar, Rajasthan": 200,
		"Jaipur, Rajasthan": 260
	},
	behror: {
		"Gurugram, Haryana": 360,
		"Manesar, Haryana": 330,
		"Faridabad, Haryana": 350,
		Delhi: 400,
		"Noida, Uttar Pradesh": 430,
		"Bhiwadi, Rajasthan": 160,
		"Neemrana, Rajasthan": 120,
		"Alwar, Rajasthan": 180,
		"Jaipur, Rajasthan": 300
	},
	bhiwadi: {
		"Gurugram, Haryana": 180,
		"Manesar, Haryana": 150,
		"Faridabad, Haryana": 170,
		Delhi: 240,
		"Noida, Uttar Pradesh": 280,
		"Bhiwadi, Rajasthan": 40,
		"Neemrana, Rajasthan": 140,
		"Alwar, Rajasthan": 220,
		"Jaipur, Rajasthan": 360
	}
};
function materialById(id) {
	return materials.find((item) => item.id === id) ?? materials[0];
}
function plantById(id) {
	return plants.find((item) => item.id === id) ?? plants[0];
}
function freightFor(plantId, delivery) {
	return freight[plantId]?.[delivery] ?? 520;
}
function inr(value) {
	return new Intl.NumberFormat("en-IN", {
		style: "currency",
		currency: "INR",
		maximumFractionDigits: 0
	}).format(value);
}
function Header() {
	const [open, setOpen] = (0, import_react.useState)(false);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
		className: "fixed inset-x-0 top-0 z-40 border-b border-fg/10 bg-ink/85 backdrop-blur-md",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto flex h-16 max-w-6xl items-center justify-between px-5 md:px-8",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("a", {
					href: "#top",
					className: "flex items-baseline gap-2",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "font-display text-2xl font-bold tracking-tight text-fg",
						children: "YFC"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "text-xs font-medium tracking-widest text-orange",
						children: "TRANSPORT"
					})]
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("nav", {
					className: "hidden items-center gap-5 lg:flex",
					"aria-label": "Primary",
					children: nav.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
						href: item.href,
						className: "text-sm text-muted transition-colors hover:text-fg",
						children: item.label
					}, item.href))
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
					href: "#order",
					className: "hidden h-11 items-center rounded-full bg-orange px-5 text-sm font-semibold text-ink lg:inline-flex",
					children: "Get Best Quote"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					className: "inline-flex h-11 w-11 items-center justify-center rounded-full border border-fg/15 text-fg lg:hidden",
					"aria-expanded": open,
					"aria-label": open ? "Close menu" : "Open menu",
					onClick: () => setOpen((value) => !value),
					children: open ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(X, { className: "size-5" }) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Menu, { className: "size-5" })
				})
			]
		}), open ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("nav", {
			className: "border-t border-fg/10 bg-navy px-5 py-4 lg:hidden",
			"aria-label": "Mobile",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
				className: "flex flex-col",
				children: nav.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
					href: item.href,
					className: "flex h-12 items-center text-base text-fg",
					onClick: () => setOpen(false),
					children: item.label
				}) }, item.href))
			})
		}) : null]
	});
}
function Hero() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
		id: "top",
		className: "relative min-h-svh scroll-mt-16 overflow-hidden",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
				src: "/media/hero.jpg",
				alt: "Tipper on the haul road at a stone crusher plant at sunrise",
				className: "absolute inset-0 h-full w-full object-cover",
				fetchPriority: "high"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", { className: "absolute inset-0 bg-linear-to-r from-ink via-ink/85 to-ink/25" }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", { className: "absolute inset-0 bg-linear-to-t from-ink via-transparent to-ink/40" }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "relative mx-auto flex min-h-svh max-w-6xl flex-col justify-end gap-10 px-5 pt-28 pb-14 md:px-8 lg:flex-row lg:items-end lg:justify-between lg:pb-20",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "max-w-xl",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "text-xs font-medium tracking-widest text-orange",
							children: "SINCE 2004"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("h1", {
							className: "mt-4 font-display text-5xl leading-none font-bold tracking-tight text-fg sm:text-6xl lg:text-7xl",
							children: [
								"Building India.",
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("br", {}),
								"Delivering Trust."
							]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "mt-5 max-w-md text-lg text-fg/90",
							children: "Premium Building Material Supply & Logistics"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "mt-3 text-sm tracking-wide text-muted",
							children: "Since 2004 • 22+ Years • 79,200+ Successful Trips"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "mt-8 flex flex-wrap gap-3",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
								href: "#order",
								className: "inline-flex h-12 items-center rounded-full bg-orange px-6 text-sm font-semibold text-ink",
								children: "Get Best Quote"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
								href: "#track",
								className: "inline-flex h-12 items-center rounded-full border border-fg/25 px-6 text-sm font-medium text-fg",
								children: "Track Order"
							})]
						})
					]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("aside", {
					className: "w-full max-w-sm rounded-2xl border border-fg/15 bg-navy/80 p-5 backdrop-blur-xl",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "flex items-center justify-between",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
								className: "font-display text-lg font-semibold",
								children: "Live Market Rates"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
								className: "inline-flex items-center gap-2 text-xs tracking-widest text-orange",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "size-1.5 rounded-full bg-orange",
									"aria-hidden": "true"
								}), "LIVE"]
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "mt-1 text-xs text-muted",
							children: "Ex-plant desk card, per ton"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("table", {
							className: "mt-4 w-full text-sm",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("thead", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("tr", {
								className: "text-left text-xs tracking-widest text-muted",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("th", {
									className: "pb-2 font-medium",
									children: "Material"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("th", {
									className: "pb-2 text-right font-medium",
									children: "Rate"
								})]
							}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("tbody", { children: materials.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("tr", {
								className: "border-t border-fg/10",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("td", {
									className: "py-2.5 text-fg",
									children: item.name
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("td", {
									className: "py-2.5 text-right font-medium tabular-nums text-fg",
									children: inr(item.rate)
								})]
							}, item.id)) })]
						})
					]
				})]
			})
		]
	});
}
var fieldClass = "h-12 w-full rounded-xl border border-fg/15 bg-ink px-3 text-sm text-fg outline-none";
function Rates() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "rates",
		className: "scroll-mt-20 border-t border-fg/10 bg-navy",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "LIVE RATES"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight",
					children: "Today’s ex-plant card."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-3 max-w-lg text-sm leading-relaxed text-muted",
					children: "These are the published desk rates. Freight is added on the quote, by plant and drop."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
					className: "mt-8 overflow-x-auto rounded-2xl border border-fg/10",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("table", {
						className: "w-full min-w-[36rem] text-left text-sm",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("thead", {
							className: "bg-ink text-xs tracking-widest text-muted uppercase",
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("tr", { children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("th", {
									className: "px-5 py-4 font-medium",
									children: "Material"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("th", {
									className: "px-5 py-4 font-medium",
									children: "Grade"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("th", {
									className: "px-5 py-4 text-right font-medium",
									children: "Rate"
								})
							] })
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("tbody", { children: materials.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("tr", {
							className: "border-t border-fg/10",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("td", {
									className: "px-5 py-4 font-medium text-fg",
									children: item.name
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("td", {
									className: "px-5 py-4 text-muted",
									children: item.grade
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("td", {
									className: "px-5 py-4 text-right font-medium tabular-nums text-fg",
									children: inr(item.rate)
								})
							]
						}, item.id)) })]
					})
				})
			]
		})
	});
}
function QuoteDesk() {
	const addTicket = useDesk((state) => state.addTicket);
	const pickedMaterialId = useDesk((state) => state.pickedMaterialId);
	const pickedPlantId = useDesk((state) => state.pickedPlantId);
	const [materialId, setMaterialId] = (0, import_react.useState)("20mm");
	const [plantId, setPlantId] = (0, import_react.useState)("kotputli");
	const [delivery, setDelivery] = (0, import_react.useState)(destinations[0]);
	const [qty, setQty] = (0, import_react.useState)("20");
	const [mediator, setMediator] = (0, import_react.useState)("");
	const [error, setError] = (0, import_react.useState)("");
	const [issued, setIssued] = (0, import_react.useState)(null);
	(0, import_react.useEffect)(() => {
		if (pickedMaterialId) setMaterialId(pickedMaterialId);
	}, [pickedMaterialId]);
	(0, import_react.useEffect)(() => {
		if (pickedPlantId) setPlantId(pickedPlantId);
	}, [pickedPlantId]);
	const preview = (0, import_react.useMemo)(() => {
		const material = materialById(materialId);
		const plant = plantById(plantId);
		const tonnes = Number(qty);
		const freight = freightFor(plantId, delivery);
		const landed = material.rate + freight;
		return {
			material,
			plant,
			tonnes: Number.isFinite(tonnes) ? tonnes : 0,
			freight,
			landed,
			total: Number.isFinite(tonnes) ? landed * tonnes : 0
		};
	}, [
		materialId,
		plantId,
		delivery,
		qty
	]);
	function submit(event) {
		event.preventDefault();
		const tonnes = Number(qty);
		if (!Number.isFinite(tonnes) || tonnes < 1 || tonnes > 500) {
			setError("Quantity must be between 1 and 500 ton.");
			return;
		}
		setError("");
		const material = materialById(materialId);
		const plant = plantById(plantId);
		const freight = freightFor(plantId, delivery);
		const ticket = {
			id: nextTicketId(),
			materialId: material.id,
			materialName: material.name,
			qty: tonnes,
			plantId: plant.id,
			plantName: plant.name,
			delivery,
			mediator: mediator.trim(),
			rate: material.rate,
			freight,
			createdAt: Date.now(),
			stage: 0
		};
		addTicket(ticket);
		setIssued(ticket);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "order",
		className: "scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "grid gap-8 lg:grid-cols-2",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "QUICK ORDER"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight",
					children: "Get Best Quote"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-3 max-w-md text-sm leading-relaxed text-muted",
					children: "Material, tonnes, plant, and drop. The ticket shows ex-plant rate plus an indicative freight for that lane."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
					onSubmit: submit,
					className: "mt-8 grid gap-4 sm:grid-cols-2",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
							className: "block text-sm text-muted",
							children: ["Material", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("select", {
								className: `${fieldClass} mt-2`,
								value: materialId,
								onChange: (event) => setMaterialId(event.target.value),
								children: materials.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", {
									value: item.id,
									children: item.name
								}, item.id))
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
							className: "block text-sm text-muted",
							children: ["Quantity (Ton)", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
								className: `${fieldClass} mt-2`,
								inputMode: "decimal",
								value: qty,
								onChange: (event) => setQty(event.target.value)
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
							className: "block text-sm text-muted sm:col-span-2",
							children: ["From Plant / Crusher", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("select", {
								className: `${fieldClass} mt-2`,
								value: plantId,
								onChange: (event) => setPlantId(event.target.value),
								children: plants.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", {
									value: item.id,
									children: item.name
								}, item.id))
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
							className: "block text-sm text-muted sm:col-span-2",
							children: ["Delivery Location", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("select", {
								className: `${fieldClass} mt-2`,
								value: delivery,
								onChange: (event) => setDelivery(event.target.value),
								children: destinations.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", {
									value: item,
									children: item
								}, item))
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
							className: "block text-sm text-muted sm:col-span-2",
							children: ["Mediator / Reference", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
								className: `${fieldClass} mt-2`,
								value: mediator,
								placeholder: "Rajpal",
								onChange: (event) => setMediator(event.target.value)
							})]
						}),
						error ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "text-sm text-orange sm:col-span-2",
							role: "alert",
							children: error
						}) : null,
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
							type: "submit",
							className: "inline-flex h-12 items-center justify-center rounded-full bg-orange px-6 text-sm font-semibold text-ink sm:col-span-2 sm:w-fit",
							children: "Get Best Quote"
						})
					]
				})
			] }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col gap-4",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(RouteCard, {
					plant: preview.plant.name,
					delivery,
					material: preview.material.name
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "rounded-2xl border border-fg/10 bg-navy p-5",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "text-xs tracking-widest text-muted",
							children: "INDICATIVE TICKET"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("dl", {
							className: "mt-4 space-y-3 text-sm",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Row, {
									label: "Ex-plant",
									value: `${inr(preview.material.rate)} / ton`
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Row, {
									label: "Freight",
									value: `${inr(preview.freight)} / ton`
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Row, {
									label: "Landed",
									value: `${inr(preview.landed)} / ton`
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Row, {
									label: "Quantity",
									value: `${preview.tonnes || 0} ton`
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "flex items-baseline justify-between border-t border-fg/10 pt-3",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("dt", {
										className: "text-muted",
										children: "Ticket total"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dd", {
										className: "font-display text-3xl font-bold tabular-nums",
										children: inr(preview.total)
									})]
								})
							]
						}),
						issued ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "mt-5 rounded-xl border border-orange/40 bg-ink p-4",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
									className: "text-xs tracking-widest text-orange",
									children: ["ORDER ", issued.id]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
									className: "mt-2 text-sm text-fg",
									children: [
										issued.materialName,
										" · ",
										issued.qty,
										" ton · ",
										issued.plantName,
										" → ",
										issued.delivery
									]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
									className: "mt-1 text-sm text-muted",
									children: ["Mediator / Reference: ", issued.mediator || "—"]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("a", {
									href: "#track",
									className: "mt-4 inline-flex text-sm font-medium text-orange",
									children: "Track this order"
								})
							]
						}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "mt-5 text-sm text-muted",
							children: "Issue a quote to lock a trackable order number on this desk."
						})
					]
				})]
			})]
		})
	});
}
function Row({ label, value }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex items-baseline justify-between gap-4",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("dt", {
			className: "text-muted",
			children: label
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dd", {
			className: "tabular-nums text-fg",
			children: value
		})]
	});
}
function RouteCard({ plant, delivery, material }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "rounded-2xl border border-fg/10 bg-ink p-5",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex items-center justify-between text-xs tracking-widest text-muted",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "LANE" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: material })]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("svg", {
				viewBox: "0 0 360 92",
				className: "mt-3 h-24 w-full text-orange",
				"aria-hidden": "true",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
						d: "M28 64 C 90 64, 110 24, 180 24 S 270 64, 332 28",
						fill: "none",
						stroke: "currentColor",
						strokeWidth: "2",
						className: "route-dash opacity-90"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("circle", {
						cx: "28",
						cy: "64",
						r: "6",
						fill: "currentColor"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("circle", {
						cx: "332",
						cy: "28",
						r: "6",
						fill: "currentColor"
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex items-start justify-between gap-4 text-sm",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "max-w-[12rem] text-fg",
					children: plant
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "max-w-[12rem] text-right text-fg",
					children: delivery
				})]
			})
		]
	});
}
function TrackDesk() {
	const tickets = useDesk((state) => state.tickets);
	const activeId = useDesk((state) => state.activeId);
	const setActive = useDesk((state) => state.setActive);
	const setStage = useDesk((state) => state.setStage);
	const [query, setQuery] = (0, import_react.useState)("");
	const [miss, setMiss] = (0, import_react.useState)(false);
	(0, import_react.useEffect)(() => {
		if (activeId) setQuery(activeId);
	}, [activeId]);
	const match = tickets.find((ticket) => ticket.id.toLowerCase() === query.trim().toLowerCase());
	function lookup(event) {
		event.preventDefault();
		const found = tickets.find((ticket) => ticket.id.toLowerCase() === query.trim().toLowerCase());
		setMiss(!found);
		if (found) setActive(found.id);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "track",
		className: "scroll-mt-20 border-t border-fg/10 bg-navy",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "TRACK ORDER"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight",
					children: "Follow the ticket."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
					onSubmit: lookup,
					className: "mt-8 flex max-w-xl flex-col gap-3 sm:flex-row",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("label", {
							className: "sr-only",
							htmlFor: "order-id",
							children: "Order number"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
							id: "order-id",
							className: fieldClass,
							placeholder: "YFC-00000",
							value: query,
							onChange: (event) => {
								setQuery(event.target.value);
								setMiss(false);
							}
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
							type: "submit",
							className: "inline-flex h-12 items-center justify-center rounded-full bg-orange px-6 text-sm font-semibold text-ink",
							children: "Track Order"
						})
					]
				}),
				miss ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-4 text-sm text-orange",
					role: "status",
					children: "No order with that number on this desk."
				}) : null,
				match ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TicketView, {
					ticket: match,
					onAdvance: setStage
				}) : null,
				tickets.length > 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
					className: "mt-8 flex flex-wrap gap-2",
					children: tickets.map((ticket) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: () => {
							setQuery(ticket.id);
							setActive(ticket.id);
							setMiss(false);
						},
						className: "h-10 rounded-full border border-fg/15 px-4 text-sm text-fg",
						children: ticket.id
					}) }, ticket.id))
				}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-6 text-sm text-muted",
					children: "Quotes you issue on this device show up here."
				})
			]
		})
	});
}
function TicketView({ ticket, onAdvance }) {
	const image = materialById(ticket.materialId).image;
	const landed = ticket.rate + ticket.freight;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("article", {
		className: "mt-8 grid gap-4 rounded-2xl border border-fg/10 bg-ink p-5 lg:grid-cols-[16rem_1fr]",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
			src: image,
			alt: `Close view of ${ticket.materialName}`,
			className: "aspect-square w-full rounded-xl object-cover"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-xs tracking-widest text-orange",
				children: ticket.id
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("h3", {
				className: "mt-2 font-display text-2xl font-semibold",
				children: [
					ticket.materialName,
					" · ",
					ticket.qty,
					" ton"
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
				className: "mt-2 text-sm text-muted",
				children: [
					ticket.plantName,
					" → ",
					ticket.delivery
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
				className: "mt-1 text-sm text-muted",
				children: [
					"Mediator / Reference: ",
					ticket.mediator || "—",
					" · Landed ",
					inr(landed),
					" / ton · Total",
					" ",
					inr(landed * ticket.qty)
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("ol", {
				className: "mt-6 grid gap-3 sm:grid-cols-4",
				children: stages.map((label, index) => {
					const done = index <= ticket.stage;
					return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", {
						className: `rounded-xl border px-3 py-3 text-sm ${done ? "border-orange/50 text-fg" : "border-fg/10 text-muted"}`,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
							className: "flex items-center gap-2",
							children: [done ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Check, {
								className: "size-4 text-orange",
								"aria-hidden": "true"
							}) : null, label]
						})
					}, label);
				})
			}),
			ticket.stage < 3 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				className: "mt-5 inline-flex h-11 items-center rounded-full border border-fg/20 px-5 text-sm text-fg",
				onClick: () => onAdvance(ticket.id, ticket.stage + 1),
				children: "Advance shipment"
			}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-5 text-sm text-orange",
				children: "Delivered"
			})
		] })]
	});
}
function Stats() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		className: "border-y border-fg/10 bg-navy",
		"aria-label": "Company record",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dl", {
			className: "mx-auto grid max-w-6xl grid-cols-2 gap-px bg-fg/10 sm:grid-cols-3 lg:grid-cols-5",
			children: stats.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "bg-navy px-5 py-8 md:px-8",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("dt", {
					className: "text-xs tracking-widest text-muted uppercase",
					children: item.label
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("dd", {
					className: "mt-2 font-display text-3xl font-bold tracking-tight text-fg",
					children: item.value
				})]
			}, item.label))
		})
	});
}
function About() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "about",
		className: "scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "grid items-center gap-10 lg:grid-cols-2",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "ABOUT US"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight text-fg sm:text-5xl",
					children: "Crush. Load. Deliver on a ticket."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "mt-6 space-y-4 text-base leading-relaxed text-muted",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "YFC Transport has moved graded building material since 2004. The yard crushes, the fleet loads, and every trip leaves with a number: material, tonnes, plant, and drop." }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "Twenty-two years on the corridor. 79,200 trips completed. Fleet partners run TATA Signa tippers — a working fleet, not a board of strangers." }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "The desk stays plain. Pick the grade, name the plant, name the site. You leave with a quote you can track from confirmed to delivered." })
					]
				})
			] }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("figure", {
				className: "overflow-hidden rounded-2xl border border-fg/10",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
					src: "/media/truck.jpg",
					alt: "Matte black ten-wheel tipper parked on crushed stone",
					className: "aspect-video w-full object-cover"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("figcaption", {
					className: "flex items-center justify-between bg-navy px-5 py-4 text-sm",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "text-fg",
						children: "TATA Signa class tipper"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "text-muted",
						children: "YFC fleet"
					})]
				})]
			})]
		})
	});
}
function Materials() {
	const pickMaterial = useDesk((state) => state.pickMaterial);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
		id: "materials",
		className: "scroll-mt-20 border-t border-fg/10 bg-navy",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-wrap items-end justify-between gap-4",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-xs tracking-widest text-orange",
					children: "MATERIALS"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-3 font-display text-4xl font-bold tracking-tight",
					children: "Five grades. One yard."
				})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "max-w-sm text-sm leading-relaxed text-muted",
					children: "Tap a grade to load it into the quote. Rates are the published ex-plant card."
				})]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
				className: "mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5",
				children: materials.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					onClick: () => {
						pickMaterial(item.id);
						document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
					},
					className: "group flex h-full w-full flex-col overflow-hidden rounded-2xl border border-fg/10 bg-ink text-left",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
						src: item.image,
						alt: "",
						className: "aspect-square w-full object-cover transition-transform duration-300 group-hover:scale-105"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
						className: "flex flex-1 flex-col gap-2 p-4",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "font-medium text-fg",
							children: item.name
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "text-sm leading-relaxed text-muted",
							children: item.use
						})]
					})]
				}) }, item.id))
			})]
		})
	});
}
function Plants() {
	const pickPlant = useDesk((state) => state.pickPlant);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
		id: "plants",
		className: "scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-xs tracking-widest text-orange",
				children: "OUR PLANTS"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
				className: "mt-3 max-w-xl font-display text-4xl font-bold tracking-tight",
				children: "Loading points on the Rajasthan–NCR run."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
				className: "mt-10 grid gap-4 lg:grid-cols-3",
				children: plants.map((plant) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					onClick: () => {
						pickPlant(plant.id);
						document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
					},
					className: "flex h-full w-full flex-col overflow-hidden rounded-2xl border border-fg/10 bg-navy text-left",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
						src: plant.image,
						alt: "",
						className: "aspect-video w-full object-cover"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
						className: "flex flex-1 flex-col gap-2 p-5",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "font-display text-xl font-semibold text-fg",
								children: plant.name
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "text-sm text-orange",
								children: plant.place
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "text-sm leading-relaxed text-muted",
								children: plant.note
							})
						]
					})]
				}) }, plant.id))
			})
		]
	});
}
function Site() {
	(0, import_react.useEffect)(() => {
		useDesk.persist.rehydrate();
	}, []);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Header, {}),
		/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("main", { children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Hero, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stats, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(About, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Materials, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Plants, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Rates, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(QuoteDesk, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TrackDesk, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Contact, {})
		] }),
		/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Footer, {})
	] });
}
var SplitComponent = Site;
//#endregion
export { SplitComponent as component };
