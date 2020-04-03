package co.smartreceipts.android.distance.editor

import co.smartreceipts.android.autocomplete.distance.DistanceAutoCompleteField
import co.smartreceipts.android.model.factory.DistanceBuilderFactory
import co.smartreceipts.android.widget.viper.BaseViperPresenter
import co.smartreceipts.core.di.scopes.FragmentScope
import javax.inject.Inject

@FragmentScope
class DistanceAutoCompletePresenter @Inject constructor(
    view: DistanceCreateEditView, interactor: DistanceCreateEditInteractor
) :
    BaseViperPresenter<DistanceCreateEditView, DistanceCreateEditInteractor>(view, interactor) {

    override fun subscribe() {

        compositeDisposable.add(view.hideAutoCompleteVisibilityClick
                .flatMap { t ->
                    when (t.type) {
                        DistanceAutoCompleteField.Location -> interactor.updateDistance(t.item, DistanceBuilderFactory(t.item)
                                .setLocationHiddenFromAutoComplete(true)
                                .build())
                        DistanceAutoCompleteField.Comment -> interactor.updateDistance(t.item, DistanceBuilderFactory(t.item)
                                .setCommentHiddenFromAutoComplete(true)
                                .build())
                        else -> throw UnsupportedOperationException("Unknown type: " + t.type)
                    }
                }
                .subscribe {
                    if (it.isPresent) {
                        view.removeValueFromAutoComplete()
                    }
                }
        )

        compositeDisposable.add(view.unHideAutoCompleteVisibilityClick
                .flatMap { t ->
                    when (t.type) {
                        DistanceAutoCompleteField.Location -> interactor.updateDistance(t.item, DistanceBuilderFactory(t.item)
                                .setLocationHiddenFromAutoComplete(false)
                                .build())
                        DistanceAutoCompleteField.Comment -> interactor.updateDistance(t.item, DistanceBuilderFactory(t.item)
                                .setCommentHiddenFromAutoComplete(false)
                                .build())
                        else -> throw UnsupportedOperationException("Unknown type: " + t.type)
                    }
                }
                .subscribe {
                    if (it.isPresent) {
                        view.unHideAutoCompleteClick()
                    } else {
                        view.displayAutoCompleteError()
                    }
                }
        )
    }

}