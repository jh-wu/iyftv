package com.iyftv.app.ui.category

import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.model.Category
import com.iyftv.app.ui.common.PagedGridViewModel

/** One category tab on the home screen. */
class CategoryViewModel(source: VideoSource, category: Category) :
    PagedGridViewModel({ page -> source.list(category, page) })
