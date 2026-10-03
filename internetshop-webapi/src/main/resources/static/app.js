// Простий клієнт демонстрації. Усі дані виводяться через textContent (без innerHTML) — захист від XSS.
'use strict';

const customerId = crypto.randomUUID();
let products = [];
const money = (v) => Number(v).toFixed(2); // JSON-число 32999.00 браузер читає як 32999


async function api(method, url, body) {
  const options = { method, headers: {} };
  if (method === 'POST') {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body ?? {});
  }
  const response = await fetch(url, options);
  if (response.status === 204) return null; // DELETE: успіх без тіла відповіді
  const data = await response.json();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${data.error}`);
  return data;
}

function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function show(text, kind) {
  const box = document.getElementById('message');
  box.textContent = text;
  box.className = kind;
}

async function run(action, successText) {
  try {
    await action();
    if (successText) show(successText, 'info');
    await refresh();
  } catch (e) {
    show(e.message, 'error');
  }
}

function renderProducts() {
  const body = document.getElementById('products');
  body.replaceChildren();
  for (const p of products) {
    const row = el('tr');
    row.append(el('td', p.name), el('td', p.description), el('td', money(p.price)));
    const idCell = el('td');
    idCell.append(el('code', p.id.slice(0, 8)));
    const remove = el('button', 'Видалити', 'secondary');
    remove.type = 'button';
    remove.addEventListener('click', () => {
      if (!confirm(`Видалити товар «${p.name}» з каталогу?`)) return;
      run(() => api('DELETE', `/products/${p.id}`), 'Товар видалено з каталогу. Уже створені замовлення не змінились.');
    });
    const actionCell = el('td');
    actionCell.append(remove);
    row.append(idCell, actionCell);
    body.append(row);
  }
}

function renderOrders(orders) {
  const box = document.getElementById('orders');
  box.replaceChildren();
  if (orders.length === 0) box.append(el('p', 'Замовлень ще немає.', 'muted'));
  for (const o of [...orders].reverse()) {
    const card = el('div', undefined, 'order');
    const title = el('p');
    title.append(el('b', `Замовлення ${o.id.slice(0, 8)}`), ' ', el('span', o.status, `status ${o.status}`),
      ` · разом: ${money(o.total)} грн`);
    card.append(title);

    const table = el('table');
    for (const l of o.lines) {
      const row = el('tr');
      row.append(el('td', l.productName), el('td', `${money(l.unitPrice)} × ${l.quantity}`), el('td', `${money(l.lineTotal)} грн`));
      table.append(row);
    }
    if (o.lines.length) card.append(table);

    const form = el('form');
    const select = el('select');
    for (const p of products) {
      const option = el('option', `${p.name} — ${money(p.price)} грн`);
      option.value = p.id;
      select.append(option);
    }
    const qty = el('input');
    qty.type = 'number'; qty.value = '1'; qty.style.width = '80px';
    const add = el('button', 'Додати');
    add.type = 'submit';
    const place = el('button', 'Оформити', 'secondary');
    place.type = 'button';
    form.append(select, qty, add, place);
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      run(() => api('POST', `/orders/${o.id}/items`, { productId: select.value, quantity: Number(qty.value) }), 'Товар додано.');
    });
    place.addEventListener('click', () => run(() => api('POST', `/orders/${o.id}/place`), 'Замовлення оформлено.'));
    card.append(form);
    box.append(card);
  }
}

async function refresh() {
  products = await api('GET', '/products');
  renderProducts();
  renderOrders(await api('GET', '/orders'));
}

document.getElementById('customer').textContent = customerId;
document.getElementById('new-order').addEventListener('click',
  () => run(() => api('POST', '/orders', { customerId }), 'Замовлення створено.'));
document.getElementById('product-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  const price = Number(String(f.get('price')).replace(',', '.'));
  run(async () => {
    await api('POST', '/products', { name: f.get('name'), description: f.get('description'), price });
    e.target.reset();
  }, 'Товар додано до каталогу.');
});

refresh().catch((e) => show(e.message, 'error'));
