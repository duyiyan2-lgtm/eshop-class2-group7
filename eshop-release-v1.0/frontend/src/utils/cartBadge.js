export const CART_UPDATED_EVENT = 'eshop:cart-updated'

export const notifyCartUpdated = () => {
  window.dispatchEvent(new Event(CART_UPDATED_EVENT))
}
